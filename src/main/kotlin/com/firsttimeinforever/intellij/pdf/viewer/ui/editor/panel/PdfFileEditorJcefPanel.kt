package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel

import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.StaticServer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.ui.jcef.JCEFHtmlPanel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter


class PdfFileEditorJcefPanel: PdfFileEditorPanel() {
    private val browserPanel = JCEFHtmlPanel("about:blank")
    private val logger = logger<PdfFileEditorJcefPanel>()
    private lateinit var virtualFile: VirtualFile
    private val eventSubscriptionsManager =
        MessageEventSubscriptionsManager.fromList(browserPanel, listOf("pageChanged"))
    private var currentPageNumberHolder = 0
    private val jsonSerializer = Json(JsonConfiguration.Stable)

    init {
        Disposer.register(this, browserPanel)
        add(browserPanel.component)
        eventSubscriptionsManager.addHandler("pageChanged") {
            val result = jsonSerializer.parse(PageChangeEventDataObject.serializer(), it)
            logger.debug(result.toString())
            currentPageNumberHolder = result.pageNumber
            null
        }
    }

    private fun triggerMessageEvent(eventName: String, data: String) {
        browserPanel.cefBrowser.executeJavaScript("triggerMessageEvent('$eventName', $data)", null, 0)
    }

    private fun addUpdateHandler() {
        val fileSystem = LocalFileSystem.getInstance()
        fileSystem.addRootToWatch(virtualFile.path, false)
        fileSystem.addVirtualFileListener(object: VirtualFileListener {
            override fun contentsChanged(event: VirtualFileEvent) {
                logger.debug("Got some events batch")
                if (event.file != virtualFile) {
                    logger.debug("Seems like target file (${virtualFile.path}) is not changed")
                    return
                }
                logger.debug("Target file (${virtualFile.path}) changed. Reloading page!")
                val targetUrl = StaticServer.getInstance()
                    ?.getFilePreviewUrl(virtualFile.path)
                browserPanel.loadURL(targetUrl!!.toExternalForm())
            }
        })
    }

    override fun openDocument(file: VirtualFile) {
        virtualFile = file
        addUpdateHandler()
        reloadDocument()
    }

    override fun reloadDocument() {
        val targetUrl = StaticServer.getInstance()
            ?.getFilePreviewUrl(virtualFile.path)!!.toExternalForm()
        logger.debug("Trying to load url: ${targetUrl}")
        browserPanel.jbCefClient.addLoadHandler(object: CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (browser!!.url != targetUrl) {
                    return
                }
                setCurrentPageNumber(currentPageNumberHolder)
            }
        }, browserPanel.cefBrowser)
        browserPanel.loadURL(targetUrl)
    }

    override fun getCurrentPageNumber(): Int {
        return currentPageNumberHolder
    }

    override fun setCurrentPageNumber(page: Int) {
        currentPageNumberHolder = page
        val data = jsonSerializer.toJson(PageChangeEventDataObject.serializer(), PageChangeEventDataObject(page))
        triggerMessageEvent("pageSet", data.toString())
    }

    override fun dispose() {
    }
}
