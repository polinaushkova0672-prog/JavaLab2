package org.example;
import org.example.ui.MazeFrame;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class WindowTest {
    private JButton button(Container root,String title) {
        for(Component c:root.getComponents()) {
            if(c instanceof JButton b&&title.equals(b.getText())) return b;
            if(c instanceof Container child) { JButton b=button(child,title); if(b!=null) return b; }
        }
        return null;
    }
    @Test public void windowConstructsPaintsAndHandlesControls() throws Exception {
        assertFalse("GUI test requires a desktop environment",GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(()->{
            MazeFrame frame=new MazeFrame();
            try {
                frame.setVisible(true); frame.validate();
                for(String title:new String[]{"Пустое поле","Сгенерировать","Применить награды","На старт","Шаг ИИ","Показать маршрут","Стоп"}) {
                    JButton b=button(frame,title); assertNotNull(title,b); b.doClick();
                }
                BufferedImage image=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=image.createGraphics(); frame.getRootPane().printAll(g); g.dispose();
                try { Files.createDirectories(Path.of("target")); ImageIO.write(image,"png",Path.of("target/window-preview.png").toFile()); }
                catch(java.io.IOException ex) { throw new AssertionError(ex); }
                assertTrue(frame.getWidth()>=900);
            } finally { frame.dispose(); }
        });
    }
    private boolean containsText(Container root,String text) {
        for(Component c:root.getComponents()) {
            if(c instanceof JLabel label&&label.getText().contains(text)) return true;
            if(c instanceof Container child&&containsText(child,text)) return true;
        }
        return false;
    }
    @Test public void trainingCompletesWithoutBlockingEventThread() throws Exception {
        MazeFrame[] frames=new MazeFrame[1];
        SwingUtilities.invokeAndWait(()->{
            frames[0]=new MazeFrame(); button(frames[0],"Пустое поле").doClick();
            button(frames[0],"Обучить").doClick();
        });
        try {
            long deadline=System.nanoTime()+20_000_000_000L; boolean[] done={false};
            while(!done[0]&&System.nanoTime()<deadline) {
                Thread.sleep(50);
                SwingUtilities.invokeAndWait(()->done[0]=containsText(frames[0],"Обучение завершено"));
            }
            assertTrue("Training must complete and leave EDT responsive",done[0]);
        } finally { SwingUtilities.invokeAndWait(()->frames[0].dispose()); }
    }
}
