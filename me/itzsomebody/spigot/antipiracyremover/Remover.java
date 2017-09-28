package me.itzsomebody.spigot.antipiracyremover;

import java.awt.event.*;
import javax.swing.*;
import java.awt.*;
import java.util.zip.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

@SuppressWarnings("serial")
public class Remover extends JFrame
{
    private static String userID;
    private static Pattern USERID_PATTERN = Pattern.compile("user_id=([^&]+)");
    private JTextField field;
    
    public static void main(String[] args) {
        createGUI();
    }
    
    private static void createGUI() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                }
                catch (Exception ex) {}
                Remover remover = new Remover();
                remover.setTitle("SpigotMC Antipiracy Remover");
                remover.setResizable(false);
                remover.setSize(440, 100);
                remover.setLocationRelativeTo(null);
                remover.setDefaultCloseOperation(3);
                remover.getContentPane().setLayout(new FlowLayout());
                JLabel label = new JLabel("Select File:");
                remover.field = new JTextField();
                remover.field.setEditable(false);
                remover.field.setColumns(18);
                JButton selectButton = new JButton("Select");
                selectButton.setToolTipText("Select jar file");
                selectButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JFileChooser chooser = new JFileChooser();
                        if (remover.field.getText() != null && !remover.field.getText().isEmpty()) {
                            chooser.setSelectedFile(new File(remover.field.getText()));
                        }
                        chooser.setMultiSelectionEnabled(false);
                        chooser.setFileSelectionMode(0);
                        int result = chooser.showOpenDialog(remover);
                        if (result == 0) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    remover.field.setText(chooser.getSelectedFile().getAbsolutePath());
                                }
                            });
                        }
                    }
                });
                JButton startButton = new JButton("Start");
                startButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (remover.field.getText() == null || remover.field.getText().isEmpty() || !remover.field.getText().endsWith(".jar")) {
                            JOptionPane.showMessageDialog(null, "You must select a valid jar file!", "Error", 0);
                            return;
                        }
                        File output = null;
                        try {
                            File input = new File(remover.field.getText());
                            if (!input.getName().endsWith(".jar")) {
                                throw new IllegalArgumentException("File must be a jar.");
                            }
                            if (!input.exists()) {
                                throw new FileNotFoundException("The file " + input.getName() + " doesn't exist.");
                            }
                            output = new File(String.format("%s-Output.jar", input.getAbsolutePath().substring(0, input.getAbsolutePath().lastIndexOf("."))));
                            if (output.exists()) {
                                output.delete();
                            }
                            process(input, output, 0);
                            if (Remover.userID == null) {
                                JOptionPane.showMessageDialog(null, "Could not find Spigot Anti-Piracy method.", "Done", 1);
                            }
                            else {
                                process(input, output, 1);
                                checkFile(output);
                                JOptionPane.showMessageDialog(null, "All IDs and Spigot Anti-Piracy method(s) and method calls removed.", "Done", 1);
                            }
                        }
                        catch (Throwable t) {
                            JOptionPane.showMessageDialog(null, t, "Error", 0);
                            t.printStackTrace();
                            if (output != null) {
                                output.delete();
                            }
                        }
                        finally {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    remover.field.setText("");
                                }
                            });
                            Remover.userID = null;
                        }
                    }
                });
                JPanel panel = new JPanel(new FlowLayout());
                panel.add(label);
                panel.add(remover.field);
                panel.add(selectButton);
                JPanel panel2 = new JPanel(new FlowLayout());
                panel2.add(startButton);
                JPanel border = new JPanel(new BorderLayout());
                border.add(panel, "North");
                border.add(panel2, "South");
                remover.getContentPane().add(border);
                remover.setVisible(true);
            }
        });
    }
    
    private static void process(File jarFile, File outputFile, int mode) throws Throwable {
        ZipFile zipFile = new ZipFile(jarFile);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        ZipOutputStream out = (mode == 1) ? new ZipOutputStream(new FileOutputStream(outputFile)) : null;
        try {
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry)entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        ClassReader cr = new ClassReader(in);
                        ClassNode classNode = new ClassNode();
                        cr.accept(classNode, 0);
                        switch (mode) {
                            case 0: {
                                if (findID(classNode)) {
                                    return;
                                }
                                break;
                            }
                            case 1: {
                                removeMethod(classNode);
                                removeID(classNode);
                                ClassWriter cw = new ClassWriter(0);
                                classNode.accept(cw);
                                ZipEntry newEntry = new ZipEntry(entry.getName());
                                newEntry.setTime(System.currentTimeMillis());
                                out.putNextEntry(newEntry);
                                writeToFile(out, new ByteArrayInputStream(cw.toByteArray()));
                                break;
                            }
                        }
                    }
                }
                else {
                    if (mode != 1) {
                        continue;
                    }
                    entry.setTime(System.currentTimeMillis());
                    out.putNextEntry(entry);
                    writeToFile(out, zipFile.getInputStream(entry));
                }
            }
        }
        finally {
            zipFile.close();
            if (out != null) {
                out.close();
            }
        }
    }
    
    private static void writeToFile(ZipOutputStream outputStream, InputStream inputStream) throws Throwable {
        byte[] buffer = new byte[4096];
        try {
            while (inputStream.available() > 0) {
                int data = inputStream.read(buffer);
                outputStream.write(buffer, 0, data);
            }
        }
        finally {
            inputStream.close();
            outputStream.closeEntry();
        }
    }
    
    private static byte[] toByteArray(InputStream in) throws Throwable {
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            while (in.available() > 0) {
                out.write(buffer, 0, in.read(buffer));
            }
        }
        finally {
            in.close();
        }
        return out.toByteArray();
    }
    
    private static boolean findID(ClassNode classNode) throws Throwable {
    	for (MethodNode methodNode : classNode.methods) {
    	      if (methodNode.name.equalsIgnoreCase("loadConfig0"))
    	      {
    	        Iterator<AbstractInsnNode> insnIterator = methodNode.instructions.iterator();
    	        while (insnIterator.hasNext())
    	        {
    	          AbstractInsnNode insnNode = (AbstractInsnNode)insnIterator.next();
    	          String str;
    	          if ((insnNode.getType() == 9) && ((str = ((LdcInsnNode)insnNode).cst.toString()).contains("spigotmc.org")))
    	          {
    	            Matcher matcher = USERID_PATTERN.matcher(str);
    	            if (matcher.find())
    	            {
    	              userID = matcher.group(1);
    	              return true;
    	            }
    	            throw new IllegalStateException("Could not find Spigot User ID.");
    	          }
    	        }
    	      }
    	    }
        return false;
    }
    
    private static void checkFile(File jarFile) throws Throwable {
        if (Remover.userID == null) {
            throw new IllegalStateException();
        }
        if (!jarFile.exists()) {
            throw new IllegalStateException("Output file not found.");
        }
        ZipFile zipFile = new ZipFile(jarFile);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        try {
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry)entries.nextElement();
                InputStream in = zipFile.getInputStream(entry);
                byte[] fileContent = toByteArray(in);
                if (new String(fileContent).contains(Remover.userID)) {
                    throw new Exception("Could not remove IDs in " + entry.getName());
                }
            }
        }
        finally {
            zipFile.close();
        }
    }
    
    private static void removeMethod(ClassNode classNode) throws Throwable {
        Iterator<MethodNode> iterator = classNode.methods.iterator();
        while (iterator.hasNext()) {
            MethodNode methodNode = iterator.next();
            if (methodNode.name.equalsIgnoreCase("onEnable")) {
                InsnList insnNodes = methodNode.instructions;
                AbstractInsnNode insnNode = insnNodes.get(0);
                if (insnNode.getType() != 5 || insnNode.getOpcode() != 184 || !((MethodInsnNode)insnNode).name.equalsIgnoreCase("loadConfig0")) {
                    continue;
                }
                insnNodes.remove(insnNode);
            }
            else {
                if (!methodNode.name.equalsIgnoreCase("loadConfig0")) {
                    continue;
                }
                iterator.remove();
            }
        }
        if (classNode.attrs != null) {
            Iterator<Attribute> attributeIterator = classNode.attrs.iterator();
            while (attributeIterator.hasNext()) {
                Attribute attribute = attributeIterator.next();
                if (attribute.type.equalsIgnoreCase("CompileVersion")) {
                    attributeIterator.remove();
                }
            }
        }
    }
    
    private static void removeID(ClassNode classNode)
    	    throws Throwable
    	  {
    	    if (userID == null) {
    	      throw new IllegalStateException();
    	    }
    	    Iterator<FieldNode> fieldIterator = classNode.fields.iterator();
    	    Iterator<MethodNode> methodIterator = classNode.methods.iterator();
    	    while (fieldIterator.hasNext())
    	    {
    	      FieldNode fieldNode = (FieldNode)fieldIterator.next();
    	      if ((fieldNode.value instanceof String))
    	      {
    	        String value = (String)fieldNode.value;
    	        if (!value.isEmpty()) {
    	          fieldNode.value = value.replace(userID, "%%__USER__%%");
    	        }
    	      }
    	    }
    	    while (methodIterator.hasNext())
    	    {
    	      MethodNode methodNode = (MethodNode)methodIterator.next();
    	      InsnList insnNodes = methodNode.instructions;
    	      
    	      Iterator<AbstractInsnNode> insnIterator = insnNodes.iterator();
    	      while (insnIterator.hasNext())
    	      {
    	        AbstractInsnNode insnNode = (AbstractInsnNode)insnIterator.next();
    	        if (insnNode.getOpcode() == 18)
    	        {
    	          Object constant = ((LdcInsnNode)insnNode).cst;
    	          if ((constant instanceof String)) {
    	            ((LdcInsnNode)insnNode).cst = ((String)((LdcInsnNode)insnNode).cst).replace(userID, "%%__USER__%%");
    	          }
    	        }
    	      }
    	    }
    	  }
}