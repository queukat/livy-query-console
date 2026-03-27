package com.queukat.livy_new

import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.queukat.livy_new.bottompanel.LivySessionsPanel
import com.queukat.livy_new.editor.LivyConsoleFileType
import com.queukat.livy_new.editor.ui.LivyConsolePanel
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JTabbedPane

@TestApplication
@RunInEdt
@Tag("screenshots")
class LivyUiScreenshotTest {

    private val outputDir: Path = Paths
        .get(System.getProperty("livy.screenshots.outputDir", "docs/screenshots"))
        .toAbsolutePath()

    @Test
    fun generateSettingsScreenshot() {
        val settings = LivyPluginSettings.getInstance().pluginState
        settings.livyServerUrl = "https://livy.company.example"
        settings.kind = "sql"
        settings.proxyUser = "analytics-user"
        settings.driverMemory = "4g"
        settings.executorMemory = "8g"
        settings.driverCores = 2
        settings.executorCores = 4
        settings.numExecutors = 4
        settings.conf = "spark.sql.shuffle.partitions=16,spark.app.name=demo"
        settings.ttl = "30m"
        settings.maxSessions = 3
        settings.sessionManagementStrategy = "reuse"
        settings.killOldestIfFull = true

        val configurable = LivyPluginConfigurable()
        val component = configurable.createComponent() as JComponent
        render(component, "settings.png", 1200, 1220)
        configurable.disposeUIResources()
    }

    @Test
    fun generateConsoleScreenshot() {
        val project = ProjectManager.getInstance().defaultProject
        val code = """
            val df = spark.range(10)
            df.selectExpr("id", "id * 10 as value").show(10, false)
        """.trimIndent()
        val file = LightVirtualFile(
            "Livy Query Console.${LivyConsoleFileType.EXTENSION}",
            code
        )

        val panel = LivyConsolePanel(project, file)
        panel.addPreviewResult(
            sessionId = 42,
            statement = Statement(
                id = 7,
                code = code,
                state = "available",
                output = StatementOutput(
                    status = "ok",
                    execution_count = 1,
                    data = mapOf(
                        "text/plain" to """
                            +---+-----+
                            |id |value|
                            +---+-----+
                            |0  |0    |
                            |1  |10   |
                            |2  |20   |
                            |3  |30   |
                            +---+-----+
                        """.trimIndent()
                    )
                )
            ),
            title = "Result Preview"
        )
        selectTab(panel, "Table")
        render(panel, "console.png", 1400, 900)
        panel.disposePanel()
    }

    @Test
    fun generateSessionsScreenshot() {
        val project = ProjectManager.getInstance().defaultProject
        val panel = LivySessionsPanel(project = project, autoRefresh = false)
        panel.replaceSessions(
            listOf(
                Session(
                    id = 42,
                    appId = "application_1700000000000_0042",
                    owner = "analytics-user",
                    kind = "sql",
                    state = "idle",
                    driverMemory = "4g",
                    executorMemory = "8g",
                    queue = "default",
                    log = listOf("Session ready")
                ),
                Session(
                    id = 43,
                    appId = "application_1700000000000_0043",
                    owner = "analytics-user",
                    kind = "pyspark",
                    state = "busy",
                    driverMemory = "2g",
                    executorMemory = "4g",
                    queue = "adhoc",
                    log = listOf("Running statement #12")
                ),
                Session(
                    id = 44,
                    appId = "application_1700000000000_0044",
                    owner = "data-team",
                    kind = "spark",
                    state = "starting",
                    driverMemory = "8g",
                    executorMemory = "8g",
                    queue = "batch",
                    log = listOf("Provisioning executors")
                )
            )
        )
        render(panel, "sessions.png", 1400, 520)
    }

    private fun render(component: JComponent, fileName: String, width: Int, height: Int) {
        Files.createDirectories(outputDir)
        val size = Dimension(width, height)
        prepare(component, size)

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = component.background ?: Color.WHITE
        graphics.fillRect(0, 0, width, height)
        component.printAll(graphics)
        graphics.dispose()

        ImageIO.write(image, "png", outputDir.resolve(fileName).toFile())
    }

    private fun selectTab(component: Component, title: String): Boolean {
        if (component is JTabbedPane) {
            for (index in 0 until component.tabCount) {
                if (component.getTitleAt(index) == title) {
                    component.selectedIndex = index
                    return true
                }
            }
        }
        if (component is Container) {
            for (child in component.components) {
                if (selectTab(child, title)) {
                    return true
                }
            }
        }
        return false
    }

    private fun prepare(component: JComponent, size: Dimension) {
        component.preferredSize = size
        component.size = size
        component.setBounds(0, 0, size.width, size.height)
        layoutRecursively(component)
    }

    private fun layoutRecursively(component: Component) {
        if (component is Container) {
            component.invalidate()
            component.doLayout()
            component.validate()
            component.components.forEach { child ->
                if (child.width <= 0 || child.height <= 0) {
                    val preferred = child.preferredSize
                    child.setSize(preferred)
                }
                layoutRecursively(child)
            }
        }
    }
}
