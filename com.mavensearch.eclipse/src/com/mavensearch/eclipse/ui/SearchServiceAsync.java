package com.mavensearch.eclipse.ui;

import java.util.function.Consumer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.PlatformUI;

import com.mavensearch.eclipse.service.SearchService.SearchResult;

/** 把 SearchService 包进后台 Job，结果回投 UI 线程。绝不在 UI 线程跑搜索。 */
final class SearchServiceAsync {

    private SearchServiceAsync() {
    }

    interface SearchRunner {
        SearchResult search(String query, int limit);
    }

    /** ui 回调已在 Display.asyncExec 中执行；query 为空时直接回空结果。 */
    static void search(SearchRunner runner, String query, int limit, Consumer<SearchResult> ui) {
        if (query == null || query.isBlank()) {
            ui.accept(new SearchResult(java.util.List.of(), false));
            return;
        }
        Job.create("Maven search", (IProgressMonitor m) -> {
            SearchResult r;
            try {
                r = runner.search(query.trim(), limit);
            } catch (RuntimeException e) {
                r = new SearchResult(java.util.List.of(), false);
            }
            final SearchResult fr = r;
            PlatformUI.getWorkbench().getDisplay().asyncExec(() -> ui.accept(fr));
            return (org.eclipse.core.runtime.IStatus) Status.OK_STATUS;
        }).schedule();
    }
}
