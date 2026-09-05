package com.kira.companion.vrm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses the glTF JSON chunk of a .vrm file (see [GlbReader]) into [VrmModelData].
 * Only VRM 0.x (`extensions.VRM`) is supported for now - it is what current VRoid
 * Studio exports and the vast majority of VRM files in the wild use. VRM 1.0
 * (`extensions.VRMC_vrm`) is detected and reported clearly rather than silently
 * mishandled.
 */
object VrmJsonParser {

    class UnsupportedVrmException(message: String) : Exception(message)

    fun parse(jsonText: String): VrmModelData {
        val root = JSONObject(jsonText)
        val nodesArray = root.optJSONArray("nodes") ?: JSONArray()

        val nodeNames: List<String?> = (0 until nodesArray.length()).map { i ->
            nodesArray.optJSONObject(i)?.optString("name")?.takeIf { it.isNotEmpty() }
        }

        val meshNodeIndex = mutableMapOf<Int, Int>()
        for (i in 0 until nodesArray.length()) {
            val node = nodesArray.optJSONObject(i) ?: continue
            if (node.has("mesh")) {
                val meshIndex = node.optInt("mesh", -1)
                if (meshIndex >= 0 && !meshNodeIndex.containsKey(meshIndex)) {
                    meshNodeIndex[meshIndex] = i
                }
            }
        }

        val extensions = root.optJSONObject("extensions")
        val vrm0 = extensions?.optJSONObject("VRM")
        val vrm1 = extensions?.optJSONObject("VRMC_vrm")

        return when {
            vrm0 != null -> parseVrm0(vrm0, nodeNames, meshNodeIndex)
            vrm1 != null -> throw UnsupportedVrmException(
                "Обнаружен VRM 1.0 (VRMC_vrm). Сейчас поддерживается только VRM 0.x. " +
                    "Экспортируйте модель как VRM 0.x (например, в VRoid Studio: " +
                    "«Экспорт» → выбрать формат VRM0) и замените assets/models/Kira.vrm.",
            )
            else -> throw UnsupportedVrmException(
                "В файле нет расширения VRM (extensions.VRM) - это обычный glTF/glb, а не VRM-модель.",
            )
        }
    }

    private fun parseVrm0(
        vrm: JSONObject,
        nodeNames: List<String?>,
        meshNodeIndex: Map<Int, Int>,
    ): VrmModelData {
        val humanoid = vrm.optJSONObject("humanoid")
        val humanBonesArray = humanoid?.optJSONArray("humanBones") ?: JSONArray()
        val humanBoneMap = mutableMapOf<VrmHumanBone, Int>()
        for (i in 0 until humanBonesArray.length()) {
            val entry = humanBonesArray.optJSONObject(i) ?: continue
            val bone = VrmHumanBone.fromVrmName(entry.optString("bone"))
            val nodeIndex = entry.optInt("node", -1)
            if (bone != null && nodeIndex >= 0) {
                humanBoneMap[bone] = nodeIndex
            }
        }

        val blendShapeGroups = parseBlendShapeGroups(vrm.optJSONObject("blendShapeMaster"))
        val springBoneChains = parseSpringBoneChains(vrm.optJSONObject("secondaryAnimation"))
        val meta = vrm.optJSONObject("meta")

        return VrmModelData(
            specVersion = VrmSpecVersion.VRM_0,
            humanBoneNodeIndices = humanBoneMap,
            nodeNames = nodeNames,
            meshNodeIndex = meshNodeIndex,
            blendShapeGroups = blendShapeGroups,
            springBoneChains = springBoneChains,
            title = meta?.optString("title")?.takeIf { it.isNotEmpty() },
            authorName = meta?.optString("author")?.takeIf { it.isNotEmpty() },
        )
    }

    private fun parseBlendShapeGroups(blendShapeMaster: JSONObject?): List<VrmBlendShapeGroup> {
        val groupsArray = blendShapeMaster?.optJSONArray("blendShapeGroups") ?: JSONArray()
        return buildList {
            for (i in 0 until groupsArray.length()) {
                val group = groupsArray.optJSONObject(i) ?: continue
                val bindsArray = group.optJSONArray("binds") ?: JSONArray()
                val binds = buildList {
                    for (j in 0 until bindsArray.length()) {
                        val bind = bindsArray.optJSONObject(j) ?: continue
                        val mesh = bind.optInt("mesh", -1)
                        val index = bind.optInt("index", -1)
                        if (mesh < 0 || index < 0) continue
                        val weight = bind.optDouble("weight", 100.0).toFloat() / 100f
                        add(VrmBlendShapeBind(meshIndex = mesh, morphTargetIndex = index, weight = weight))
                    }
                }
                add(
                    VrmBlendShapeGroup(
                        name = group.optString("name"),
                        presetName = group.optString("presetName", "unknown"),
                        binds = binds,
                    ),
                )
            }
        }
    }

    private fun parseSpringBoneChains(secondaryAnimation: JSONObject?): List<VrmSpringBoneChain> {
        val boneGroupsArray = secondaryAnimation?.optJSONArray("boneGroups") ?: JSONArray()
        return buildList {
            for (i in 0 until boneGroupsArray.length()) {
                val boneGroup = boneGroupsArray.optJSONObject(i) ?: continue
                val bonesArray = boneGroup.optJSONArray("bones") ?: JSONArray()
                val roots = (0 until bonesArray.length()).map { bonesArray.optInt(it) }
                if (roots.isEmpty()) continue
                add(
                    VrmSpringBoneChain(
                        rootNodeIndices = roots,
                        stiffness = boneGroup.optDouble("stiffiness", 1.0).toFloat(),
                        gravityPower = boneGroup.optDouble("gravityPower", 0.0).toFloat(),
                        dragForce = boneGroup.optDouble("dragForce", 0.4).toFloat(),
                        hitRadius = boneGroup.optDouble("hitRadius", 0.02).toFloat(),
                    ),
                )
            }
        }
    }
}
