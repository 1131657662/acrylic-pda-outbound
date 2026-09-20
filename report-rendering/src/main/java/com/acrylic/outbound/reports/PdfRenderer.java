package com.acrylic.outbound.reports;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;

public final class PdfRenderer {
    public static final int MAX_RENDER_ROWS = 20_000;
    public void render(ReportSnapshot snapshot, OutputStream output) throws IOException {
        SnapshotValidator.validate(snapshot);
        long rows = snapshot.orders().stream().mapToLong(o -> 2L * o.originalLines().size()
                + o.products().size() + o.adjustments().size() + o.exceptions().size()).sum();
        Values.require(rows <= MAX_RENDER_ROWS, "PDF最多20000个渲染行，请固定快照后按订单拆分或导出Excel");
        String html = PdfHtml.document(snapshot);
        try (var playwright = Playwright.create(); var browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true).setTimeout(60_000))) {
            try (var context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false)
                    .setAcceptDownloads(false).setServiceWorkers(ServiceWorkerPolicy.BLOCK).setOffline(true))) {
                context.route("**/*", route -> route.abort());
                var page = context.newPage(); page.setDefaultTimeout(60_000);
                page.setContent(html, new Page.SetContentOptions().setWaitUntil(WaitUntilState.LOAD).setTimeout(60_000));
                page.evaluate("() => document.fonts.ready");
                Values.require((Boolean) page.evaluate("() => document.fonts.check('12px ReportCJK')"), "中文字体未就绪");
                byte[] pdf = page.pdf(new Page.PdfOptions().setFormat("A4").setPrintBackground(true)
                        .setPreferCSSPageSize(true).setDisplayHeaderFooter(false));
                PdfMetadata.write(pdf, snapshot, output);
            }
        }
    }
}
