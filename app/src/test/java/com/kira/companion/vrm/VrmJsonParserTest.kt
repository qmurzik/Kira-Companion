package com.kira.companion.vrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VrmJsonParserTest {

    private val sampleVrm0Json = """
        {
          "nodes": [
            {"name": "Root"},
            {"name": "J_Bip_C_Head"},
            {"name": "Face", "mesh": 0}
          ],
          "extensions": {
            "VRM": {
              "meta": {"title": "TestKira", "author": "Tester"},
              "humanoid": {
                "humanBones": [
                  {"bone": "head", "node": 1}
                ]
              },
              "blendShapeMaster": {
                "blendShapeGroups": [
                  {
                    "name": "Joy",
                    "presetName": "joy",
                    "binds": [
                      {"mesh": 0, "index": 2, "weight": 100.0}
                    ]
                  }
                ]
              },
              "secondaryAnimation": {
                "boneGroups": [
                  {
                    "comment": "hair",
                    "stiffiness": 1.5,
                    "gravityPower": 0.2,
                    "dragForce": 0.4,
                    "hitRadius": 0.02,
                    "bones": [1]
                  }
                ]
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parses humanoid bones`() {
        val data = VrmJsonParser.parse(sampleVrm0Json)

        assertEquals(VrmSpecVersion.VRM_0, data.specVersion)
        assertEquals(1, data.humanBoneNodeIndices[VrmHumanBone.HEAD])
        assertEquals("J_Bip_C_Head", data.nodeNameFor(1))
        assertTrue(data.isUsable())
    }

    @Test
    fun `parses mesh to node mapping`() {
        val data = VrmJsonParser.parse(sampleVrm0Json)

        assertEquals(2, data.meshNodeIndex[0])
        assertEquals("Face", data.nodeNameForMesh(0))
    }

    @Test
    fun `parses blend shape groups with normalized weights`() {
        val data = VrmJsonParser.parse(sampleVrm0Json)

        val joy = data.blendShapeGroup("Joy")
        assertTrue(joy != null)
        assertEquals(1, joy!!.binds.size)
        assertEquals(0, joy.binds[0].meshIndex)
        assertEquals(2, joy.binds[0].morphTargetIndex)
        assertEquals(1.0f, joy.binds[0].weight, 0.001f)

        // Lookup by preset name should also work.
        assertEquals(joy, data.blendShapeGroup("joy"))
    }

    @Test
    fun `parses spring bone chains`() {
        val data = VrmJsonParser.parse(sampleVrm0Json)

        assertEquals(1, data.springBoneChains.size)
        val chain = data.springBoneChains[0]
        assertEquals(listOf(1), chain.rootNodeIndices)
        assertEquals(1.5f, chain.stiffness, 0.001f)
        assertEquals(0.2f, chain.gravityPower, 0.001f)
    }

    @Test
    fun `parses meta title and author`() {
        val data = VrmJsonParser.parse(sampleVrm0Json)

        assertEquals("TestKira", data.title)
        assertEquals("Tester", data.authorName)
    }

    @Test(expected = VrmJsonParser.UnsupportedVrmException::class)
    fun `rejects a plain glTF file with no VRM extension`() {
        VrmJsonParser.parse("""{"nodes": [], "extensions": {}}""")
    }

    @Test(expected = VrmJsonParser.UnsupportedVrmException::class)
    fun `reports VRM 1_0 as unsupported instead of silently mishandling it`() {
        VrmJsonParser.parse("""{"nodes": [], "extensions": {"VRMC_vrm": {"specVersion": "1.0"}}}""")
    }

    @Test
    fun `a model with no head bone is not usable`() {
        val data = VrmJsonParser.parse("""{"nodes": [], "extensions": {"VRM": {"humanoid": {"humanBones": []}}}}""")
        assertTrue(!data.isUsable())
        assertNull(data.title)
    }
}
