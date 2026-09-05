package com.kira.companion.behavior

import com.kira.companion.vrm.VrmSpringBoneChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpringBoneSimulatorTest {

    private fun chain(stiffness: Float = 1.5f, gravityPower: Float = 0f, dragForce: Float = 0.4f) =
        VrmSpringBoneChain(
            rootNodeIndices = listOf(1),
            stiffness = stiffness,
            gravityPower = gravityPower,
            dragForce = dragForce,
            hitRadius = 0.02f,
        )

    @Test
    fun `starts at rest with zero angle`() {
        val simulator = SpringBoneSimulator(chain(), jointCount = 3)
        assertTrue(simulator.jointStates.all { it.angleDegrees == 0f })
    }

    @Test
    fun `no driving motion and no gravity stays at rest`() {
        val simulator = SpringBoneSimulator(chain(gravityPower = 0f), jointCount = 2)
        repeat(120) { simulator.update(deltaSeconds = 1f / 60f, drivingAngleDeltaDegrees = 0f) }

        for (joint in simulator.jointStates) {
            assertEquals(0f, joint.angleDegrees, 0.01f)
        }
    }

    @Test
    fun `a single frame of driving motion gives the joint nonzero velocity`() {
        val simulator = SpringBoneSimulator(chain(), jointCount = 1)

        simulator.update(deltaSeconds = 1f / 60f, drivingAngleDeltaDegrees = 20f)

        assertTrue(abs(simulator.jointStates[0].angularVelocity) > 0f)
    }

    @Test
    fun `sustained driving motion displaces the joint then it settles back once it stops`() {
        val simulator = SpringBoneSimulator(chain(), jointCount = 1)

        // A sustained head turn over several frames should visibly drag the hair along.
        repeat(30) { simulator.update(deltaSeconds = 1f / 60f, drivingAngleDeltaDegrees = 20f) }
        val displaced = simulator.jointStates[0].angleDegrees
        assertTrue("expected a meaningful displacement after sustained driving motion, was $displaced", abs(displaced) > 0.3f)

        repeat(300) { simulator.update(deltaSeconds = 1f / 60f, drivingAngleDeltaDegrees = 0f) }
        val settled = simulator.jointStates[0].angleDegrees
        assertTrue("expected the joint to settle back near rest, was $settled", abs(settled) < 1f)
    }

    @Test
    fun `higher gravity biases the resting angle away from zero`() {
        val noGravity = SpringBoneSimulator(chain(gravityPower = 0f), jointCount = 1)
        val withGravity = SpringBoneSimulator(chain(gravityPower = 0.5f), jointCount = 1)

        repeat(600) {
            noGravity.update(deltaSeconds = 1f / 60f, drivingAngleDeltaDegrees = 0f)
            withGravity.update(deltaSeconds = 1f / 60f, drivingAngleDeltaDegrees = 0f)
        }

        assertEquals(0f, noGravity.jointStates[0].angleDegrees, 0.01f)
        assertTrue(abs(withGravity.jointStates[0].angleDegrees) > 0.5f)
    }

    @Test
    fun `stiffer chains displace less than soft ones under the same sustained motion`() {
        val soft = SpringBoneSimulator(chain(stiffness = 0.5f), jointCount = 1)
        val stiff = SpringBoneSimulator(chain(stiffness = 4f), jointCount = 1)

        repeat(30) {
            soft.update(deltaSeconds = 1f / 60f, drivingAngleDeltaDegrees = 20f)
            stiff.update(deltaSeconds = 1f / 60f, drivingAngleDeltaDegrees = 20f)
        }

        assertTrue(abs(stiff.jointStates[0].angleDegrees) < abs(soft.jointStates[0].angleDegrees))
    }

    @Test
    fun `zero or negative delta time is a no-op`() {
        val simulator = SpringBoneSimulator(chain(), jointCount = 1)
        simulator.update(deltaSeconds = 1f / 60f, drivingAngleDeltaDegrees = 20f)
        val before = simulator.jointStates[0].angleDegrees

        simulator.update(deltaSeconds = 0f, drivingAngleDeltaDegrees = 99f)
        simulator.update(deltaSeconds = -1f, drivingAngleDeltaDegrees = 99f)

        assertEquals(before, simulator.jointStates[0].angleDegrees, 0.0001f)
    }
}
