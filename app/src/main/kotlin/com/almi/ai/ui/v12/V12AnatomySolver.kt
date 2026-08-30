package com.almi.ai.ui.v12

import kotlin.math.sqrt

internal data class V12AnatomyPoint(
    val x: Float,
    val y: Float,
    val z: Float,
)

internal data class V12AnatomyRig(
    val pelvis: V12AnatomyPoint?,
    val spine1: V12AnatomyPoint? = null,
    val spine2: V12AnatomyPoint? = null,
    val spine3: V12AnatomyPoint? = null,
    val neck: V12AnatomyPoint?,
    val head: V12AnatomyPoint? = null,
    val leftShoulder: V12AnatomyPoint? = null,
    val rightShoulder: V12AnatomyPoint? = null,
    val leftUpperArm: V12AnatomyPoint? = null,
    val rightUpperArm: V12AnatomyPoint? = null,
    val leftElbow: V12AnatomyPoint? = null,
    val rightElbow: V12AnatomyPoint? = null,
    val leftHand: V12AnatomyPoint? = null,
    val rightHand: V12AnatomyPoint? = null,
    val leftThigh: V12AnatomyPoint? = null,
    val rightThigh: V12AnatomyPoint? = null,
    val leftCalf: V12AnatomyPoint? = null,
    val rightCalf: V12AnatomyPoint? = null,
    val leftFoot: V12AnatomyPoint? = null,
    val rightFoot: V12AnatomyPoint? = null,
    val forwardHint: V12AnatomyPoint = V12AnatomyPoint(0f, 0f, 1f),
)

/**
 * Converts a humanoid rig into stable tailoring landmarks.
 *
 * The visual mesh can change without changing the measurement contract. Torso points are anchored
 * to the neck→pelvis axis and softly blended toward real spine bones when available. Bust points
 * use shoulder width plus an anterior offset so overlays sit on the front surface rather than
 * inside the chest. All optional bones have safe fallbacks.
 */
internal object V12AnatomySolver {
    fun solve(rig: V12AnatomyRig): Map<String, V12AnatomyPoint> {
        val result = linkedMapOf<String, V12AnatomyPoint>()

        fun put(name: String, value: V12AnatomyPoint?) {
            if (value != null) result[name] = value
        }

        put("pelvis", rig.pelvis)
        put("spine1", rig.spine1)
        put("spine2", rig.spine2)
        put("spine3", rig.spine3)
        put("neck", rig.neck)
        put("head", rig.head)
        put("leftShoulder", rig.leftShoulder)
        put("rightShoulder", rig.rightShoulder)
        put("leftUpperArm", rig.leftUpperArm)
        put("rightUpperArm", rig.rightUpperArm)
        put("leftElbow", rig.leftElbow)
        put("rightElbow", rig.rightElbow)
        put("leftHand", rig.leftHand)
        put("rightHand", rig.rightHand)
        put("leftThigh", rig.leftThigh)
        put("rightThigh", rig.rightThigh)
        put("leftCalf", rig.leftCalf)
        put("rightCalf", rig.rightCalf)
        put("leftFoot", rig.leftFoot)
        put("rightFoot", rig.rightFoot)

        val neck = rig.neck
        val pelvis = rig.pelvis
        if (neck != null && pelvis != null) {
            val axisChest = lerp(neck, pelvis, .28f)
            val axisUnderbust = lerp(neck, pelvis, .40f)
            val axisWaist = lerp(neck, pelvis, .61f)
            val axisAbdomen = lerp(neck, pelvis, .75f)
            val axisHips = lerp(neck, pelvis, .93f)

            result["chest"] = blend(axisChest, rig.spine3, .42f)
            result["underbust"] = blend(axisUnderbust, rig.spine2, .28f)
            result["waist"] = blend(axisWaist, rig.spine1, .34f)
            result["abdomen"] = blend(axisAbdomen, rig.spine1, .12f)
            result["hips"] = blend(axisHips, pelvis, .30f)
        }

        val leftShoulder = rig.leftShoulder
        val rightShoulder = rig.rightShoulder
        if (leftShoulder != null && rightShoulder != null) {
            val center = midpoint(leftShoulder, rightShoulder)
            result["shoulderCenter"] = center

            val chest = result["chest"]
            if (chest != null) {
                val shoulderVector = subtract(rightShoulder, leftShoulder)
                val torsoVector = if (neck != null && pelvis != null) subtract(pelvis, neck) else V12AnatomyPoint(0f, -1f, 0f)
                val rawForward = normalize(cross(shoulderVector, torsoVector))
                val forward = if (dot(rawForward, rig.forwardHint) < 0f) scale(rawForward, -1f) else rawForward
                val shoulderWidth = length(shoulderVector)
                val anterior = scale(forward, shoulderWidth * .075f)
                val halfSpan = scale(subtract(rightShoulder, center), .40f)
                result["leftBust"] = add(add(chest, scale(halfSpan, -1f)), anterior)
                result["rightBust"] = add(add(chest, halfSpan), anterior)
            }
        }

        if (rig.head != null && neck != null) {
            result["crown"] = add(rig.head, scale(subtract(rig.head, neck), .62f))
        }

        return result
    }

    private fun blend(axis: V12AnatomyPoint, bone: V12AnatomyPoint?, boneWeight: Float): V12AnatomyPoint {
        if (bone == null) return axis
        val safeWeight = boneWeight.coerceIn(0f, .48f)
        return V12AnatomyPoint(
            x = axis.x * (1f - safeWeight) + bone.x * safeWeight,
            y = axis.y * (1f - safeWeight) + bone.y * safeWeight,
            z = axis.z * (1f - safeWeight) + bone.z * safeWeight,
        )
    }

    private fun lerp(a: V12AnatomyPoint, b: V12AnatomyPoint, t: Float): V12AnatomyPoint {
        val safe = t.coerceIn(0f, 1f)
        return V12AnatomyPoint(
            x = a.x + (b.x - a.x) * safe,
            y = a.y + (b.y - a.y) * safe,
            z = a.z + (b.z - a.z) * safe,
        )
    }

    private fun midpoint(a: V12AnatomyPoint, b: V12AnatomyPoint) = V12AnatomyPoint(
        (a.x + b.x) * .5f,
        (a.y + b.y) * .5f,
        (a.z + b.z) * .5f,
    )

    private fun add(a: V12AnatomyPoint, b: V12AnatomyPoint) = V12AnatomyPoint(a.x + b.x, a.y + b.y, a.z + b.z)
    private fun subtract(a: V12AnatomyPoint, b: V12AnatomyPoint) = V12AnatomyPoint(a.x - b.x, a.y - b.y, a.z - b.z)
    private fun scale(a: V12AnatomyPoint, factor: Float) = V12AnatomyPoint(a.x * factor, a.y * factor, a.z * factor)

    private fun cross(a: V12AnatomyPoint, b: V12AnatomyPoint) = V12AnatomyPoint(
        a.y * b.z - a.z * b.y,
        a.z * b.x - a.x * b.z,
        a.x * b.y - a.y * b.x,
    )

    private fun dot(a: V12AnatomyPoint, b: V12AnatomyPoint): Float = a.x * b.x + a.y * b.y + a.z * b.z
    private fun length(a: V12AnatomyPoint): Float = sqrt(a.x * a.x + a.y * a.y + a.z * a.z)

    private fun normalize(a: V12AnatomyPoint): V12AnatomyPoint {
        val value = length(a)
        if (value < 1e-6f) return V12AnatomyPoint(0f, 0f, 1f)
        return scale(a, 1f / value)
    }
}