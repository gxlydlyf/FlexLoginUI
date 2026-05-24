package com.gxlydlyf.flexloginui;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.*;

public class DialogEncoder {

    // ----- Text Component 帮助类 -----
    public static class TextComponent {
        public String text;
        public String color;

        public TextComponent(String text) {
            this.text = text;
        }

        public TextComponent(String text, String color) {
            this.text = text;
            this.color = color;
        }
    }

    // ----- Action -----
    public static class DialogAction {
        public String type;
        // 普通 action
        public String command;
        public String url;
        public String value;
        // dynamic/custom
        public String customId;
        public ByteBuf customAdditions; // 改为直接存储 ByteBuf
        // dynamic/run_command
        public String commandTemplate;

        public DialogAction(String type) {
            this.type = type;
        }

        public void writeTo(ByteBuf buf) {
            List<Runnable> children = new ArrayList<>();
            children.add(() -> NbtEncoder.tagString(buf, "type", type));

            switch (type) {
                // 原有普通类型
                case "run_command":
                case "suggest_command":
                    if (command != null)
                        children.add(() -> NbtEncoder.tagString(buf, "command", command));
                    break;
                case "open_url":
                    if (url != null)
                        children.add(() -> NbtEncoder.tagString(buf, "url", url));
                    break;
                case "copy_to_clipboard":
                    if (value != null)
                        children.add(() -> NbtEncoder.tagString(buf, "value", value));
                    break;

                // dynamic/custom: 动态自定义数据包
                case "dynamic/custom":
                    if (customId != null)
                        children.add(() -> NbtEncoder.tagString(buf, "id", customId));
                    // additions 是可选的，如果存在且非空则写入复合标签
                    // 直接写入 additions 数据
                    if (customAdditions != null && customAdditions.isReadable()) {
                        children.add(() -> {
                            NbtEncoder.startTag(buf, 10, "additions"); // 复合标签
                            buf.writeBytes(customAdditions);
                            buf.writeByte(0x00); // 结束复合标签
                        });
                    }
                    break;

                // dynamic/run_command: 动态命令模板
                case "dynamic/run_command":
                    if (commandTemplate != null)
                        children.add(() -> NbtEncoder.tagString(buf, "template", commandTemplate));
                    break;
            }

            NbtEncoder.writeCompoundPayload(buf, children);
        }

        // ---------------- 静态操作构建方法 ----------------

        /**
         * 创建 run_command 操作
         *
         * @param command 要运行的命令（不需要 / 前缀）
         */
        public static DialogAction createRunCommand(String command) {
            DialogAction action = new DialogAction("run_command");
            action.command = command;
            return action;
        }

        /**
         * 创建 suggest_command 操作（将命令填入聊天栏，不自动执行）
         *
         * @param command 要建议的命令
         */
        public static DialogAction createSuggestCommand(String command) {
            DialogAction action = new DialogAction("suggest_command");
            action.command = command;
            return action;
        }

        /**
         * 创建 open_url 操作
         *
         * @param url 要打开的URL（必须是 http:// 或 https://）
         */
        public static DialogAction createOpenUrl(String url) {
            DialogAction action = new DialogAction("open_url");
            action.url = url;
            return action;
        }

        /**
         * 创建 copy_to_clipboard 操作
         *
         * @param value 要复制到剪贴板的文本
         */
        public static DialogAction createCopyToClipboard(String value) {
            DialogAction action = new DialogAction("copy_to_clipboard");
            action.value = value;
            return action;
        }

        // ---------------- 动态操作构建方法 ----------------

        /**
         * 创建 dynamic/custom 动态自定义数据包
         * 根据输入值构建custom点击事件，发送自定义负载到服务端
         *
         * @param id        网络负载的命名空间ID，格式如 "mymod:login"
         * @param additions 可选的静态负载，添加到网络负载中（可以为null）
         *                  游戏会先添加additions中的静态数据，再添加输入控件的动态数据
         * @return DialogAction
         * <p>
         * 示例：
         * <pre>
         * // 无静态负载
         * DialogAction action = DialogAction.createDynamicCustom("myplugin:login", null);
         * </pre>
         */
        public static DialogAction createDynamicCustom(String id, ByteBuf additions) {
            DialogAction action = new DialogAction("dynamic/custom");
            action.customId = id;
            action.customAdditions = additions;
            return action;
        }

        public static DialogAction createDynamicCustom(String id) {
            return createDynamicCustom(id, null);
        }

        /**
         * 创建 dynamic/run_command 动态命令模板
         * 根据输入值构建run_command点击事件，发送命令并使服务端执行
         *
         * @param template 命令模板，使用 $(参数名) 格式引用输入控件的值
         *                 参数名必须与输入控件的 key 匹配
         * @return DialogAction
         * <p>
         * 示例：
         * <pre>
         * // 假设有 key 为 "username" 和 "password" 的输入控件
         * DialogAction action = DialogAction.createDynamicRunCommand("login $(username) $(password)");
         * </pre>
         * <p>
         * 注意：
         * - 命令模板不需要 / 或 $ 前缀
         * - 必须至少包含一个模板参数
         * - 无对应输入控件的参数会被替换为空字符串
         */
        public static DialogAction createDynamicRunCommand(String template) {
            DialogAction action = new DialogAction("dynamic/run_command");
            action.commandTemplate = template;
            return action;
        }
    }

    // ----- Button -----
    public static class Button {
        public TextComponent label;
        public TextComponent tooltip;
        public Integer width;
        public DialogAction action;

        public Button(TextComponent label, DialogAction action) {
            this.label = label;
            this.action = action;
        }

        public Button(TextComponent label) {
            this.label = label;
        }

        public void writeTo(ByteBuf buf) {
            List<Runnable> children = new ArrayList<>();
            children.add(() -> writeTextComponentTag(buf, "label", label));
            if (tooltip != null) children.add(() -> writeTextComponentTag(buf, "tooltip", tooltip));
            if (width != null) children.add(() -> NbtEncoder.tagInt(buf, "width", width));
            if (action != null) {
                children.add(() -> NbtEncoder.startTag(buf, 10, "action"));
                children.add(() -> action.writeTo(buf));
            }
            NbtEncoder.writeCompoundPayload(buf, children);
        }
    }

    // ----- Body Element -----
    public static class BodyElement {
        public String type;               // minecraft:plain_message, minecraft:item 等
        public TextComponent contents;    // for plain_message
        public Integer width;

        public BodyElement(String type) {
            this.type = type;
        }

        public void writeTo(ByteBuf buf) {
            List<Runnable> children = new ArrayList<>();
            children.add(() -> NbtEncoder.tagString(buf, "type", type));
            if ("minecraft:plain_message".equals(type)) {
                if (contents != null) children.add(() -> writeTextComponentTag(buf, "contents", contents));
                if (width != null) children.add(() -> NbtEncoder.tagInt(buf, "width", width));
            }
            // 可扩展其他 body 类型
            NbtEncoder.writeCompoundPayload(buf, children);
        }
    }

    // ----- Input Control -----
    public static class InputControl {
        public String type;            // minecraft:text, minecraft:boolean, minecraft:single_option, etc.
        public String key;
        public TextComponent label;
        public Integer width;
        public Integer maxLength;
        public String initial;
        public Boolean labelVisible;

        public InputControl(String type, String key, TextComponent label) {
            this.type = type;
            this.key = key;
            this.label = label;
        }

        public void writeTo(ByteBuf buf) {
            List<Runnable> children = new ArrayList<>();
            children.add(() -> NbtEncoder.tagString(buf, "type", type));
            children.add(() -> NbtEncoder.tagString(buf, "key", key));
            children.add(() -> writeTextComponentTag(buf, "label", label));

            if ("minecraft:text".equals(type)) {
                if (width != null) children.add(() -> NbtEncoder.tagInt(buf, "width", width));
                if (labelVisible != null)
                    children.add(() -> NbtEncoder.tagByte(buf, "label_visible", labelVisible ? 1 : 0));
                if (initial != null) children.add(() -> NbtEncoder.tagString(buf, "initial", initial));
                if (maxLength != null) children.add(() -> NbtEncoder.tagInt(buf, "max_length", maxLength));
            }
            // 可扩展其他 input 类型
            NbtEncoder.writeCompoundPayload(buf, children);
        }
    }

    // ----- 主对话框定义 -----
    public static class DialogDefinition {
        public String type;                            // minecraft:notice, minecraft:multi_action, ...
        public TextComponent title;
        public List<BodyElement> body = new ArrayList<>();
        public List<InputControl> inputs = new ArrayList<>();
        public List<Button> actions = new ArrayList<>();  // 用于 multi_action
        public Integer columns;
        public Boolean canCloseWithEscape = true;
        public Boolean pause = true;

        public void writeTo(ByteBuf buf) {
            List<Runnable> children = new ArrayList<>();
            children.add(() -> NbtEncoder.tagString(buf, "type", type));
            children.add(() -> writeTextComponentTag(buf, "title", title));

            // body
            if (!body.isEmpty()) {
                List<Runnable> bodyPayloads = new ArrayList<>();
                for (BodyElement be : body) {
                    bodyPayloads.add(() -> be.writeTo(buf));
                }
                children.add(() -> NbtEncoder.tagList(buf, "body", 10, bodyPayloads));
            }

            // inputs
            if (!inputs.isEmpty()) {
                List<Runnable> inpPayloads = new ArrayList<>();
                for (InputControl ic : inputs) {
                    inpPayloads.add(() -> ic.writeTo(buf));
                }
                children.add(() -> NbtEncoder.tagList(buf, "inputs", 10, inpPayloads));
            }

            // actions (用于 multi_action)
            if ("minecraft:multi_action".equals(type) && !actions.isEmpty()) {
                List<Runnable> actPayloads = new ArrayList<>();
                for (Button btn : actions) {
                    actPayloads.add(() -> btn.writeTo(buf));
                }
                children.add(() -> NbtEncoder.tagList(buf, "actions", 10, actPayloads));
            }

            // 布尔字段
            children.add(() -> NbtEncoder.tagByte(buf, "can_close_with_escape", canCloseWithEscape ? 1 : 0));
            children.add(() -> NbtEncoder.tagByte(buf, "pause", pause ? 1 : 0));

            // columns (可选)
            if (columns != null) {
                children.add(() -> NbtEncoder.tagInt(buf, "columns", columns));
            }

            NbtEncoder.writeRootCompoundStart(buf);     // 根标签头 0x00 0x0A
            // 根负载写入
            NbtEncoder.writeCompoundPayload(buf, children);   // 复合标签结束标记
        }
    }

    // ---------- 辅助: 写入 TextComponent 标签 ----------
    private static void writeTextComponentTag(ByteBuf buf, String name, TextComponent comp) {
        if (comp.color == null) {
            // 纯字符串
            NbtEncoder.tagString(buf, name, comp.text);
        } else {
            // 复合组件 {text, color}
            List<Runnable> children = new ArrayList<>();
            children.add(() -> NbtEncoder.tagString(buf, "text", comp.text));
            children.add(() -> NbtEncoder.tagString(buf, "color", comp.color));
            NbtEncoder.tagCompound(buf, name, children);
        }
    }

    // ---------- 生成 1.21.6 完整数据包 ByteBuf (包ID 0x85 开头，不含长度前缀) ----------
    public static ByteBuf buildPacket(DialogDefinition dialog) {
        ByteBuf buf = Unpooled.buffer();
        NbtEncoder.writeVarInt(buf, 0x85);          // 包 ID
        dialog.writeTo(buf);                        // 内部负载
        return buf;
    }

    // ---------- 快捷构造: 简单测试 Notice 对话框 ----------

    /**
     * 创建一个简单的通知对话框
     *
     * @param titleText    标题文本
     * @param titleColor   标题颜色
     * @param messageText  消息正文
     * @param messageColor 消息颜色
     * @param width        宽度（像素）
     * @return DialogDefinition
     */
    public static DialogDefinition createTestNoticeDialog(String titleText, String titleColor,
                                                          String messageText, String messageColor,
                                                          int width) {
        DialogDefinition def = new DialogDefinition();
        def.type = "minecraft:notice";
        def.title = new TextComponent(titleText, titleColor);

        // body 内容
        BodyElement bodyElement = new BodyElement("minecraft:plain_message");
        bodyElement.contents = new TextComponent(messageText, messageColor);
        bodyElement.width = width;
        def.body.add(bodyElement);

        // notice 类型通常只有一个按钮，这里添加默认的确认按钮
        DialogAction closeAction = new DialogAction("run_command");
        closeAction.command = "";  // 空命令，仅关闭对话框
        Button closeBtn = new Button(new TextComponent("确认", "green"), closeAction);
        def.actions.add(closeBtn);

        def.canCloseWithEscape = false;
        def.pause = true;

        return def;
    }

    // 重载方法：使用默认值
    public static DialogDefinition createTestNoticeDialog() {
        return createTestNoticeDialog("系统提示", "gold", "Hello", "white", 100);
    }
}