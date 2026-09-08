package com.wittycat.langchain4j;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * 示例 9:多模态 —— 让模型"看图说话"。
 *
 * 运行方式: mvn -q compile exec:java -Dexec.mainClass=com.wittycat.langchain4j.Example9_Multimodal
 *
 * 【知识点】
 * 1. 多模态消息 = 一条 UserMessage 里放多个 Content 段:TextContent(文字指令)
 *    + ImageContent(图片)。图片支持两种来源:
 *      - 远程 URL:  ImageContent.from(URI.create("https://..."));
 *      - base64:   ImageContent.from(base64Data, mimeType),本例运行时现场画一张 PNG 再编码。
 * 2. 本例的图是用 Java2D 现场画的柱状图(不依赖任何图片文件),问题问"几根柱子、
 *    最高的哪根、数值多少"——答案画的时候就确定了,方便验证模型是不是真看懂了。
 * 3. 看图能力取决于模型是否支持视觉。实测 glm-5.3-flash 支持图片输入,能准确读出
 *    柱子数量与数值;若换成不支持视觉的模型会收到报错,此时用 -Dglm.model=视觉模型 切换
 *    即可,代码不用改——这就是接口抽象的价值。(详见 README 常见问题。)
 * 4. 多模态与工具/记忆可自由组合:比如"看图回答 + 结果存入记忆",LangChain4j
 *    里都是同一套 ChatModel/AiServices 接口。
 */
public class Example9_Multimodal {

    public static void main(String[] args) throws IOException {
        // 1. 现场画一张柱状图并转成 base64(真实项目里通常是用户上传的图片或图片 URL)
        String base64Image = drawBarChart();
        System.out.println("已生成柱状图 PNG(" + base64Image.length() + " 字节 base64)");

        // 2. 多模态消息:文字 + 图片,放在同一条 UserMessage 里
        UserMessage message = UserMessage.from(
                TextContent.from("""
                        这是一张柱状图,请回答:
                        1) 图中有几根柱子,分别标注了什么项目?
                        2) 最高的柱子是哪一项?数值大约是多少?
                        """),
                ImageContent.from(base64Image, "image/png"));

        // 3. 调用方式与纯文本完全一样,只是消息里多了图片段
        ChatModel model = GlmModels.createChatModel();
        ChatResponse response = model.chat(message);
        System.out.println("\n模型看图回答:\n" + response.aiMessage().text());
    }

    /** 用 Java2D 画一张 480x320 的柱状图,返回 PNG 的 base64 编码 */
    private static String drawBarChart() throws IOException {
        BufferedImage image = new BufferedImage(480, 320, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 背景与标题
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 480, 320);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("2025 Programming Language Score", 60, 40);

        // 坐标轴
        g.setStroke(new BasicStroke(2));
        g.drawLine(60, 260, 440, 260); // x 轴
        g.drawLine(60, 60, 60, 260);   // y 轴

        // 四根柱子:Java 60, Python 70, Go 40, Rust 25(最高的是 Python,答案画的时候就定了,方便核对模型是否看懂)
        int[][] bars = {{60, 70, 40, 25}};
        int[] values = bars[0];
        String[] labels = {"Java", "Python", "Go", "Rust"};
        Color[] colors = {new Color(0xE76F00), new Color(0x3776AB), new Color(0x00ADD8), new Color(0xDEA584)};
        for (int i = 0; i < values.length; i++) {
            int height = values[i] * 2; // 2px 每分
            int x = 90 + i * 90;
            g.setColor(colors[i]);
            g.fillRect(x, 260 - height, 60, height);
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.drawString(labels[i], x + 8, 280);
            g.drawString(String.valueOf(values[i]), x + 22, 260 - height - 8);
        }
        g.dispose();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }
}
