package com.kira.companion.behavior

import com.kira.companion.vrm.VrmSpringBoneChain

/** Simulated sway state for one spring bone joint (a hair strand segment, a ponytail link, ...). */
data class SpringBoneJointState(
    var angleDegrees: Float = 0f,
    var angularVelocity: Float = 0f,
)

/**
 * A lightweight physics simulation for a VRM "spring bone" chain (hair/cloth secondary
 * animation): each joint sways away from its rest angle in response to the driving motion
 * above it (e.g. the head turning), pulled back by [VrmSpringBoneChain.stiffness], damped
 * by [VrmSpringBoneChain.dragForce], and biased by [VrmSpringBoneChain.gravityPower] -
 * a simplified analog of the VRM spec's spring-bone algorithm (no bone-to-bone collision,
 * since Kira's hair/clothing isn't expected to intersect anything else in the scene, and
 * collision checks would cost more than a phone-friendly hair sway needs).
 *
 * Pure Kotlin, operating on abstract per-joint sway angles rather than actual bone world
 * transforms, so it's directly unit-testable. [com.kira.companion.render.VrmRig] is
 * responsible for turning each joint's angle into an actual bone rotation once a node
 * index is resolved to a bone entity.
 */
class SpringBoneSimulator(private val chain: VrmSpringBoneChain, jointCount: Int) {

    private val joints = List(jointCount) { SpringBoneJointState() }

    val jointStates: List<SpringBoneJointState> get() = joints

    /**
     * Advances the simulation by [deltaSeconds]. [drivingAngleDeltaDegrees] is how much the
     * root driving this chain (e.g. the head, for hair) rotated just this frame - a sudden
     * turn should visibly drag the first joint a moment behind it, which then propagates
     * down the rest of the chain like a whip.
     */
    fun update(deltaSeconds: Float, drivingAngleDeltaDegrees: Float) {
        if (deltaSeconds <= 0f) return

        var incomingForce = drivingAngleDeltaDegrees
        for (joint in joints) {
            val previousAngle = joint.angleDegrees
            val restoringForce = -joint.angleDegrees * chain.stiffness
            val gravityBias = chain.gravityPower * GRAVITY_SCALE
            val damping = joint.angularVelocity * chain.dragForce * DRAG_SCALE
            val acceleration = incomingForce * PROPAGATION_FACTOR + restoringForce + gravityBias - damping

            joint.angularVelocity += acceleration * deltaSeconds
            joint.angleDegrees = (joint.angleDegrees + joint.angularVelocity * deltaSeconds)
                .coerceIn(-MAX_ANGLE_DEGREES, MAX_ANGLE_DEGREES)

            incomingForce = joint.angleDegrees - previousAngle
        }
    }

    private companion object {
        const val PROPAGATION_FACTOR = 0.6f
        const val GRAVITY_SCALE = 8f
        const val DRAG_SCALE = 10f
        const val MAX_ANGLE_DEGREES = 45f
    }
}
