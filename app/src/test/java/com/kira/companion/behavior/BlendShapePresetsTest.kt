package com.kira.companion.behavior

import com.kira.companion.model.KiraEmotion
import com.kira.companion.vrm.VrmJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlendShapePresetsTest {

    @Test
    fun `every emotion has more than one blend shape target`() {
        for (emotion in KiraEmotion.entries) {
            val weights = BlendShapePresets.weightsFor(emotion)
            assertTrue(
                "${emotion.name} should combine multiple blend shapes, not a single on-off face",
                weights.size > 1 || emotion == KiraEmotion.IDLE,
            )
        }
    }

    @Test
    fun `happy combines joy and fun`() {
        val weights = BlendShapePresets.weightsFor(KiraEmotion.HAPPY)
        assertTrue(weights.any { it.presetOrGroupName == VrmBlendShapePreset.JOY })
        assertTrue(weights.any { it.presetOrGroupName == VrmBlendShapePreset.FUN })
    }

    @Test
    fun `resolve drops groups the model does not define`() {
        val json = """
            {
              "nodes": [],
              "extensions": {
                "VRM": {
                  "humanoid": {"humanBones": [{"bone": "head", "node": 0}]},
                  "blendShapeMaster": {
                    "blendShapeGroups": [
                      {"name": "Joy", "presetName": "joy", "binds": []}
                    ]
                  }
                }
              }
            }
        """.trimIndent()
        val modelData = VrmJsonParser.parse(json)

        val resolved = BlendShapePresets.resolve(KiraEmotion.HAPPY, modelData)

        // "joy" exists in the model (as "Joy") and resolves; "fun" doesn't exist, so it's dropped.
        assertEquals(1, resolved.size)
        assertEquals(0.8f, resolved["Joy"]!!, 0.001f)
    }
}
