package com.almi.ai.ui.v12

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V12AnatomySolverTest {
    private val neck = V12AnatomyPoint(0f, 1f, 0f)
    private val pelvis = V12AnatomyPoint(0f, 0f, 0f)

    @Test
    fun torsoLandmarksRemainOrderedFromNeckToPelvis() {
        val solved = V12AnatomySolver.solve(
            V12AnatomyRig(
                pelvis = pelvis,
                neck = neck,
                spine1 = V12AnatomyPoint(0f, .43f, 0f),
                spine2 = V12AnatomyPoint(0f, .61f, 0f),
                spine3 = V12AnatomyPoint(0f, .76f, 0f),
            )
        )

        val chest = solved.getValue("chest").y
        val underbust = solved.getValue("underbust").y
        val waist = solved.getValue("waist").y
        val abdomen = solved.getValue("abdomen").y
        val hips = solved.getValue("hips").y

        assertTrue(chest > underbust)
        assertTrue(underbust > waist)
        assertTrue(waist > abdomen)
        assertTrue(abdomen > hips)
        assertTrue(chest < neck.y && hips > pelvis.y)
    }

    @Test
    fun bustPointsStaySymmetricAndInFrontOfChest() {
        val solved = V12AnatomySolver.solve(
            V12AnatomyRig(
                pelvis = pelvis,
                neck = neck,
                leftShoulder = V12AnatomyPoint(-.30f, .82f, 0f),
                rightShoulder = V12AnatomyPoint(.30f, .82f, 0f),
                forwardHint = V12AnatomyPoint(0f, 0f, 1f),
            )
        )

        val left = solved.getValue("leftBust")
        val right = solved.getValue("rightBust")
        val chest = solved.getValue("chest")

        assertEquals(-right.x, left.x, .0001f)
        assertEquals(right.y, left.y, .0001f)
        assertEquals(right.z, left.z, .0001f)
        assertTrue(left.z > chest.z)
        assertTrue(right.z > chest.z)
    }

    @Test
    fun crownExtendsBeyondHeadAwayFromNeck() {
        val head = V12AnatomyPoint(0f, 1.18f, 0f)
        val solved = V12AnatomySolver.solve(
            V12AnatomyRig(
                pelvis = pelvis,
                neck = neck,
                head = head,
            )
        )

        assertTrue(solved.getValue("crown").y > head.y)
    }

    @Test
    fun missingOptionalBonesStillProducesSafeTorso() {
        val solved = V12AnatomySolver.solve(
            V12AnatomyRig(
                pelvis = pelvis,
                neck = neck,
            )
        )

        assertTrue(solved.containsKey("chest"))
        assertTrue(solved.containsKey("waist"))
        assertTrue(solved.containsKey("hips"))
        assertTrue(!solved.containsKey("leftBust"))
    }

    @Test
    fun torsoFractionsStayInsideNeckPelvisEnvelope() {
        val solved = V12AnatomySolver.solve(V12AnatomyRig(pelvis = pelvis, neck = neck))
        listOf("chest", "underbust", "waist", "abdomen", "hips").forEach { name ->
            val y = solved.getValue(name).y
            assertTrue("$name escaped torso envelope", y in pelvis.y..neck.y)
        }
    }
}