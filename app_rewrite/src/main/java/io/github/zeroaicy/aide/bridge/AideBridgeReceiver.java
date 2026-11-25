package io.github.zeroaicy.aide.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.aide.ui.ServiceContainer;
import com.aide.ui.services.ProjectSupport;
import com.aide.ui.services.TemplateService;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

/**
 * AIDE MCP Bridge
 *
 * 通过 adb 广播触发，供 PC 端 AI / MCP 工具调用。
 * 支持：create（创建项目）、build（编译当前项目）、status（查询状态）、ping（探活）。
 *
 * 调用示例：
 * adb shell am broadcast -a io.github.zeroaicy.aide.AIDE_BRIDGE \
 *   -n <pkg>/io.github.zeroaicy.aide.bridge.AideBridgeReceiver \
 *   --es action create --es req_id r1 --es type android --es name MyApp --es package com.example.myapp
 *
 * 回执文件：<externalFilesDir>/aide-bridge/result.json
 */
public class AideBridgeReceiver extends BroadcastReceiver {

    public static final String ACTION = "io.github.zeroaicy.aide.AIDE_BRIDGE";

    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_REQ_ID = "req_id";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_LOCATION = "location";

    private static final String RESULT_DIR = "aide-bridge";
    private static final String RESULT_FILE = "result.json";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) {
            return;
        }
        String action = intent.getStringExtra(EXTRA_ACTION);
        final String reqId = intent.getStringExtra(EXTRA_REQ_ID);
        final String type = intent.getStringExtra(EXTRA_TYPE);
        final String name = intent.getStringExtra(EXTRA_NAME);
        final String pkg = intent.getStringExtra(EXTRA_PACKAGE);
        final String location = intent.getStringExtra(EXTRA_LOCATION);
        final Context appContext = context.getApplicationContext();

        // ping / status 同步处理，create / build 异步处理（避免阻塞广播线程）
        if ("ping".equals(action)) {
            writeResult(appContext, reqId, "ok", "ping", "alive", "", null);
            return;
        }
        if ("status".equals(action)) {
            doStatus(appContext, reqId);
            return;
        }
        if ("build".equals(action)) {
            writeResult(appContext, reqId, "running", "build", "", "", null);
            postToUi(new Runnable() {
                @Override
                public void run() {
                    doBuild(appContext, reqId);
                }
            });
            return;
        }
        if ("create".equals(action)) {
            writeResult(appContext, reqId, "running", "create", "", "", null);
            doCreate(appContext, reqId, type, name, pkg, location);
            return;
        }

        writeResult(appContext, reqId, "error", action, "", "unknown action: " + action, null);
    }

    /** 切到 UI 线程执行；ServiceContainer 未初始化时退化为直接执行 */
    private static void postToUi(Runnable r) {
        try {
            if (!ServiceContainer.aj(r)) {
                r.run();
            }
        } catch (Throwable t) {
            r.run();
        }
    }

    private static void doStatus(Context context, String reqId) {
        try {
            String project = "";
            boolean opened = false;
            try {
                com.aide.ui.services.ProjectService ps = ServiceContainer.getProjectService();
                if (ps != null) {
                    opened = ps.isOpenProject();
                    project = ps.getCurrentAppHome();
                    if (project == null) {
                        project = "";
                    }
                }
            } catch (Throwable ignored) {
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\"opened\":").append(opened).append(",\"types\":[\"android\",\"game\",\"java\",\"cpp\",\"native\",\"phonegap\",\"website\",\"javascript\"]}");
            writeResult(context, reqId, "ok", "status", project, sb.toString(), null);
        } catch (Throwable t) {
            writeResult(context, reqId, "error", "status", "", describe(t), null);
        }
    }

    private static void doBuild(final Context context, final String reqId) {
        try {
            com.aide.ui.services.ProjectService ps = ServiceContainer.getProjectService();
            if (ps == null) {
                writeResult(context, reqId, "error", "build", "", "AIDE 未初始化，请先打开 APP", null);
                return;
            }
            if (!ps.isOpenProject()) {
                writeResult(context, reqId, "error", "build", "", "没有已打开的项目", null);
                return;
            }
            String project = ps.getCurrentAppHome();
            ps.buildProject(false);
            writeResult(context, reqId, "ok", "build", project == null ? "" : project,
                    "{\"triggered\":true}", null);
        } catch (Throwable t) {
            writeResult(context, reqId, "error", "build", "", describe(t), null);
        }
    }

    private static void doCreate(final Context context, final String reqId, final String type,
                                 final String name, final String packageName, final String location) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (TextUtils.isEmpty(name)) {
                        writeResult(context, reqId, "error", "create", "", "缺少参数 name", null);
                        return;
                    }
                    if (ServiceContainer.getTemplateService() == null) {
                        writeResult(context, reqId, "error", "create", "", "AIDE 未初始化，请先打开 APP", null);
                        return;
                    }

                    TemplateService.TemplateGroup group = findTemplateGroup(type);
                    if (group == null) {
                        writeResult(context, reqId, "error", "create", "",
                                "未知项目类型: " + type + "，可选 android/game/java/cpp/native/phonegap/website/javascript", null);
                        return;
                    }

                    String dir = location;
                    if (TextUtils.isEmpty(dir)) {
                        try {
                            dir = ServiceContainer.getFileBrowserService().DW();
                        } catch (Throwable ignored) {
                            dir = "";
                        }
                    }
                    if (TextUtils.isEmpty(dir)) {
                        writeResult(context, reqId, "error", "create", "", "无法获取默认项目目录", null);
                        return;
                    }

                    String pkg = packageName;
                    if (TextUtils.isEmpty(pkg)) {
                        try {
                            pkg = ServiceContainer.getTemplateService().tp(name);
                        } catch (Throwable ignored) {
                            pkg = "";
                        }
                    }

                    final String finalDir = dir;
                    final String finalPkg = pkg;
                    // j6 内部会解压模板并替换占位符，属于 IO 操作，放后台线程
                    final List<String> createdFiles = ServiceContainer.getTemplateService()
                            .j6(dir, name, pkg, "", group);
                    final String home = ServiceContainer.getTemplateService().Hw(dir, name);

                    postToUi(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                String openPath = home;
                                if (group.Hw != null && group.Hw.length() > 0) {
                                    openPath = home + "/" + group.Hw;
                                }
                                // 与手动创建一致：设置文件浏览目录并打开项目
                                try {
                                    ServiceContainer.getFileBrowserService().Hw(openPath);
                                } catch (Throwable ignored) {
                                }
                                try {
                                    ServiceContainer.getMainActivity().Ws(false);
                                } catch (Throwable ignored) {
                                }
                                try {
                                    ServiceContainer.getProjectService().XX(openPath);
                                    ServiceContainer.getProjectService().sy("created");
                                } catch (Throwable ignored) {
                                }
                                try {
                                    ServiceContainer.getOpenFileService().er(createdFiles);
                                } catch (Throwable ignored) {
                                }

                                JSONObject extra = new JSONObject();
                                extra.put("type", type);
                                extra.put("name", name);
                                extra.put("package", finalPkg);
                                extra.put("location", finalDir);
                                extra.put("project", openPath);
                                extra.put("file_count", createdFiles == null ? 0 : createdFiles.size());
                                writeResult(context, reqId, "ok", "create", openPath,
                                        extra.toString(), null);
                            } catch (Throwable t) {
                                writeResult(context, reqId, "error", "create", "", describe(t), null);
                            }
                        }
                    });
                } catch (Throwable t) {
                    writeResult(context, reqId, "error", "create", "", describe(t), null);
                }
            }
        }, "aide-bridge-create").start();
    }

    /**
     * 按 type 在所有 ProjectSupport 的模板组中查找。
     * 先按模板 zip 名精确匹配，再按显示名/类型名模糊匹配。
     */
    private static TemplateService.TemplateGroup findTemplateGroup(String type) {
        if (TextUtils.isEmpty(type)) {
            return null;
        }
        String t = type.trim().toLowerCase();
        String zip = null;
        if ("android".equals(t) || "app".equals(t)) {
            zip = "GradleApp.zip";
        } else if ("game".equals(t) || "mobilegame".equals(t) || "mobile_game".equals(t)) {
            zip = "course_game_libgdx_project.zip";
        } else if ("java".equals(t)) {
            zip = "JavaConsole.zip";
        } else if ("cpp".equals(t) || "c".equals(t) || "c++".equals(t)) {
            zip = "CppHelloWorld.zip";
        } else if ("native".equals(t) || "ndk".equals(t)) {
            zip = "JniHelloWorld.zip";
        } else if ("phonegap".equals(t) || "cordova".equals(t)) {
            zip = "PhoneGapAppNew.zip";
        } else if ("website".equals(t) || "web".equals(t)) {
            zip = "Website.zip";
        } else if ("javascript".equals(t) || "js".equals(t)) {
            zip = "HelloJavaScript.zip";
        }

        try {
            ProjectSupport[] supports = ServiceContainer.getProjectSupports();
            if (supports == null) {
                return null;
            }
            for (int i = 0; i < supports.length; i++) {
                ProjectSupport support = supports[i];
                if (support == null) {
                    continue;
                }
                TemplateService.TemplateGroup[] groups = support.getTemplateGroups();
                if (groups == null) {
                    continue;
                }
                for (int j = 0; j < groups.length; j++) {
                    TemplateService.TemplateGroup group = groups[j];
                    if (group == null) {
                        continue;
                    }
                    if (zip != null && zip.equalsIgnoreCase(group.templateResourceName)) {
                        return group;
                    }
                    if (matchName(group, t)) {
                        return group;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean matchName(TemplateService.TemplateGroup group, String t) {
        if ("android".equals(t) || "app".equals(t)) {
            return contains(group, "android app");
        }
        if ("game".equals(t) || "mobilegame".equals(t) || "mobile_game".equals(t)) {
            return contains(group, "mobile game") || contains(group, "libgdx");
        }
        if ("java".equals(t)) {
            return contains(group, "java application");
        }
        if ("cpp".equals(t) || "c".equals(t) || "c++".equals(t)) {
            return contains(group, "console application") || contains(group, "c/c++");
        }
        if ("native".equals(t) || "ndk".equals(t)) {
            return contains(group, "native android app") || contains(group, "jnihello");
        }
        if ("phonegap".equals(t) || "cordova".equals(t)) {
            return contains(group, "phonegap");
        }
        if ("website".equals(t) || "web".equals(t)) {
            return contains(group, "website");
        }
        if ("javascript".equals(t) || "js".equals(t)) {
            return contains(group, "javascript application");
        }
        return false;
    }

    private static boolean contains(TemplateService.TemplateGroup group, String keyword) {
        String k = keyword.toLowerCase();
        if (group.name != null && group.name.toLowerCase().contains(k)) {
            return true;
        }
        if (group.templateResourceName != null && group.templateResourceName.toLowerCase().contains(k)) {
            return true;
        }
        if (group.template != null) {
            if (group.template.templateName != null && group.template.templateName.toLowerCase().contains(k)) {
                return true;
            }
            if (group.template.tp != null && group.template.tp.toLowerCase().contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Throwable t) {
        if (t == null) {
            return "unknown error";
        }
        String msg = t.getMessage();
        if (TextUtils.isEmpty(msg)) {
            return t.getClass().getSimpleName();
        }
        return t.getClass().getSimpleName() + ": " + msg;
    }

    /**
     * 写回执文件。字段：status / req_id / action / project / message / extra
     */
    private static void writeResult(Context context, String reqId, String status, String action,
                                    String project, String message, JSONObject extra) {
        try {
            File dir = new File(context.getExternalFilesDir(null), RESULT_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            JSONObject json = new JSONObject();
            json.put("status", status);
            json.put("req_id", reqId == null ? "" : reqId);
            json.put("action", action == null ? "" : action);
            json.put("project", project == null ? "" : project);
            json.put("message", message == null ? "" : message);
            json.put("ts", System.currentTimeMillis());
            if (extra != null) {
                json.put("data", extra);
            }
            FileWriter writer = new FileWriter(new File(dir, RESULT_FILE), false);
            try {
                writer.write(json.toString());
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
