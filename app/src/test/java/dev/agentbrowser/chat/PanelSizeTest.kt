package dev.agentbrowser.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelSizeTest {
    @Test fun draggingUpGrowsThePanel() = assertEquals(0.6f, dragPanel(0.5f, dragPx = -100f, areaPx = 1000f), 0.0001f)
    @Test fun draggingDownShrinksThePanel() = assertEquals(0.4f, dragPanel(0.5f, dragPx = 100f, areaPx = 1000f), 0.0001f)
    @Test fun cannotGrowPastTheWholeArea() = assertEquals(1f, dragPanel(0.9f, dragPx = -500f, areaPx = 1000f), 0.0001f)
    @Test fun cannotShrinkBelowZero() = assertEquals(0f, dragPanel(0.1f, dragPx = 500f, areaPx = 1000f), 0.0001f)
    @Test fun unknownAreaLeavesSizeAlone() = assertEquals(0.5f, dragPanel(0.5f, dragPx = -100f, areaPx = 0f), 0.0001f)

    @Test fun releasedNearTheTopBecomesFullScreen() = assertEquals(PanelRest.Full, restPanel(0.95f))
    @Test fun releasedNearTheBottomCloses() = assertEquals(PanelRest.Closed, restPanel(0.1f))
    @Test fun releasedInBetweenStaysThere() = assertEquals(PanelRest.Partial(0.7f), restPanel(0.7f))
    @Test fun smallPanelIsHeldAtTheMinimum() = assertEquals(PanelRest.Partial(0.25f), restPanel(0.2f))
}
