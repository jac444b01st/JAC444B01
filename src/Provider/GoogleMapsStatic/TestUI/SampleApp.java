/**
 * Created by JFormDesigner on Mon Apr 21 12:50:34 EDT 2008
 **/

package Provider.GoogleMapsStatic.TestUI;

import Provider.GoogleMapsStatic.*;
import Task.*;
import Task.Manager.*;
import Task.ProgressMonitor.*;
import Task.Support.CoreSupport.*;
import Task.Support.GUISupport.*;
import com.jgoodies.forms.factories.*;
import info.clearthought.layout.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;

import javax.imageio.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.beans.*;
import java.io.*;
import java.text.*;
import java.util.Properties;
import java.util.concurrent.*;

/** @author nazmul idris */
public class SampleApp extends JFrame {
/**XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
* data members
*XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
reference to task **/
private SimpleTask _task;
/** this might be null. holds the image to display in a popup */
private BufferedImage _img;
/** this might be null. holds the text in case image doesn't display */
private String _respStr;

final JFileChooser fileSel = new JFileChooser(); /**<- ADDED CODE**/
private int curZoom; /**written by herman wu**/
final JFrame zframe = new JFrame("Google Static Map"); /**written by herman wu**/
String uri;


/**XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
* main method...
*XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
**/
public static void main(String[] args) {
  Utils.createInEDT(SampleApp.class);
}

/**XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
* constructor
*XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
**/
private void doInit() {
  GUIUtils.setAppIcon(this, "burn.png");
  GUIUtils.centerOnScreen(this);
  setVisible(true);

  int W = 28, H = W;
  boolean blur = false;
  float alpha = .7f;

  try {
    btnGetMap.setIcon(ImageUtils.loadScaledBufferedIcon("ok1.png", W, H, blur, alpha));
    btnQuit.setIcon(ImageUtils.loadScaledBufferedIcon("charging.png", W, H, blur, alpha));
    /**btnSavImg.setIcon(ImageUtils.loadScaledBufferedIcon("saveimg.png", W, H, blur, alpha));//ADDED CODE**/
  }
  catch (Exception e) {
    System.out.println(e);
  }

  _setupTask();
}

/** create a test task and wire it up with a task handler that dumps output to the textarea **/
@SuppressWarnings("unchecked")
private void _setupTask() {

  TaskExecutorIF<ByteBuffer> functor = new TaskExecutorAdapter<ByteBuffer>() {
    public ByteBuffer doInBackground(Future<ByteBuffer> swingWorker,
                                     SwingUIHookAdapter hook) throws Exception
    {

      _initHook(hook);

      /** set the license key**/
      MapLookup.setLicenseKey(ttfLicense.getText());
      /** get the uri for the static map**/
      uri = MapLookup.getMap(Double.parseDouble(ttfLat.getText()),
                                    Double.parseDouble(ttfLon.getText()),
                                    Integer.parseInt(ttfSizeW.getText()),
                                    Integer.parseInt(ttfSizeH.getText()),
                                    /**Integer.parseInt(ttfZoom.getText())**/
                                    curZoom);
      //);
      sout("Google Maps URI=" + uri);

      /** get the map from Google**/
      GetMethod get = new GetMethod(uri);
      new HttpClient().executeMethod(get);

      ByteBuffer data = HttpUtils.getMonitoredResponse(hook, get);

      try {
        _img = ImageUtils.toCompatibleImage(ImageIO.read(data.getInputStream()));
        sout("converted downloaded data to image...");
      }
      catch (Exception e) {
        _img = null;
        sout("The URI is not an image. Data is downloaded, can't display it as an image.");
        _respStr = new String(data.getBytes());
      }

      return data;
    }

    @Override public String getName() {
      return _task.getName();
    }
  };

  _task = new SimpleTask(
      new TaskManager(),
      functor,
      "HTTP GET Task",
      "Download an image from a URL",
      AutoShutdownSignals.Daemon
  );

  _task.addStatusListener(new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      sout(":: task status change - " + ProgressMonitorUtils.parseStatusMessageFrom(evt));
      lblProgressStatus.setText(ProgressMonitorUtils.parseStatusMessageFrom(evt));
    }
  });

  _task.setTaskHandler(new
      SimpleTaskHandler<ByteBuffer>() {
        @Override public void beforeStart(AbstractTask task) {
          sout(":: taskHandler - beforeStart");
        }
        @Override public void started(AbstractTask task) {
          sout(":: taskHandler - started ");
        }
        /** {@link SampleApp#_initHook} adds the task status listener, which is removed here */
        @Override public void stopped(long time, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- stopped");
          sout(":: time = " + time / 1000f + "sec");
          task.getUIHook().clearAllStatusListeners();
        }
        @Override public void interrupted(Throwable e, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- interrupted - " + e.toString());
        }
        @Override public void ok(ByteBuffer value, long time, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- ok - size=" + (value == null
              ? "null"
              : value.toString()));
          if (_img != null) {
            _displayImgInFrame();
          }
          else _displayRespStrInFrame();

        }
        @Override public void error(Throwable e, long time, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- error - " + e.toString());
        }
        @Override public void cancelled(long time, AbstractTask task) {
          sout(" :: taskHandler [" + task.getName() + "]- cancelled");
        }
      }
  );
}

private SwingUIHookAdapter _initHook(SwingUIHookAdapter hook) {
  hook.enableRecieveStatusNotification(checkboxRecvStatus.isSelected());
  hook.enableSendStatusNotification(checkboxSendStatus.isSelected());

  hook.setProgressMessage(ttfProgressMsg.getText());

  PropertyChangeListener listener = new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      SwingUIHookAdapter.PropertyList type = ProgressMonitorUtils.parseTypeFrom(evt);
      int progress = ProgressMonitorUtils.parsePercentFrom(evt);
      String msg = ProgressMonitorUtils.parseMessageFrom(evt);

      progressBar.setValue(progress);
      progressBar.setString(type.toString());

      sout(msg);
    }
  };

  hook.addRecieveStatusListener(listener);
  hook.addSendStatusListener(listener);
  hook.addUnderlyingIOStreamInterruptedOrClosed(new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      sout(evt.getPropertyName() + " fired!!!");
    }
  });

  return hook;
}

private void _displayImgInFrame() {

  GUIUtils.setAppIcon(zframe, "71.png");
  zframe.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

  JLabel imgLbl = new JLabel(new ImageIcon(_img));
  imgLbl.setToolTipText(MessageFormat.format("<html>Image downloaded from URI<br>size: w={0}, h={1}</html>",
                                             _img.getWidth(), _img.getHeight()));
  
  imgLbl.addMouseListener(new MouseListener() {
    public void mouseClicked(MouseEvent e) {}
    public void mousePressed(MouseEvent e) { zframe.dispose();}
    public void mouseReleased(MouseEvent e) { }
    public void mouseEntered(MouseEvent e) { }
    public void mouseExited(MouseEvent e) { }
  });
  
  zframe.setContentPane(imgLbl);
  zframe.pack();

  GUIUtils.centerOnScreen(zframe);
  zframe.setVisible(true);
  enableZoom(); /** <- Written by herman wu**/
  enableSaveEml(); /**<- ADDED CODE**/
}

private void _displayRespStrInFrame() {

  final JFrame frame = new JFrame("Google Static Map - Error");
  GUIUtils.setAppIcon(frame, "69.png");
  frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
  
  JTextArea response = new JTextArea(_respStr, 25, 80);
  response.addMouseListener(new MouseListener() {
    public void mouseClicked(MouseEvent e) {}
    public void mousePressed(MouseEvent e) { frame.dispose();}
    public void mouseReleased(MouseEvent e) { }
    public void mouseEntered(MouseEvent e) { }
    public void mouseExited(MouseEvent e) { }
  });

  frame.setContentPane(new JScrollPane(response));
  frame.pack();

  GUIUtils.centerOnScreen(frame);
  frame.setVisible(true);
}

/** simply dump status info to the textarea **/
private void sout(final String s) {
  Runnable soutRunner = new Runnable() {
    public void run() {
      if (ttaStatus.getText().equals("")) {
        ttaStatus.setText(s);
      }
      else {
        ttaStatus.setText(ttaStatus.getText() + "\n" + s);
      }
    }
  };

  if (ThreadUtils.isInEDT()) {
    soutRunner.run();
  }
  else {
    SwingUtilities.invokeLater(soutRunner);
  }
}

private void startTaskAction() {
  try {
    _task.execute();
  }
  catch (TaskException e) {
    sout(e.getMessage());
  }
}
/**ADDED CODE ->**/
private void saveImageSys(){
  if(_img != null){
    int retVal = fileSel.showSaveDialog(SampleApp.this);
    if (retVal == JFileChooser.APPROVE_OPTION) {
      File file = fileSel.getSelectedFile();
      try {
        ImageIO.write(_img, "png", file);
      } catch(IOException e) {
        System.out.println("Error - Saving Image");
      }
    }
  }
}

private void initSaveDiag(){
  fileSel.setMultiSelectionEnabled(false);
  fileSel.setAcceptAllFileFilterUsed(false);
  FileNameExtensionFilter  filter = new FileNameExtensionFilter ("JPG, PNG and BMP", new String[] { "JPG", "PNG", "BMP" });
  fileSel.setFileFilter(filter);
}

private void enableSaveEml(){
  if(_img != null){
    btnSavImg.setEnabled(true);
    btnSndEml.setEnabled(true);
  }
}

private void crtPrstList(){
  cmbPresets.addItem("None");
  cmbPresets.addItem("Canada");
  cmbPresets.addItem("United States");
  cmbPresets.addItem("Europe");
  cmbPresets.addItem("Asia");
  cmbPresets.addItem("Toronto");
  cmbPresets.addItem("Seneca");
}

private void changeSet(){
  String selected = (String)cmbPresets.getSelectedItem();
  if(selected == "Canada")
    setPreset("512","512","67","-100","3");
  else if(selected == "United States")
    setPreset("512","512","40","-100","4");
  else if(selected == "Europe")
    setPreset("512","512","50","14","4");
  else if(selected == "Asia")
    setPreset("512","512","43","-80","6");
  else if(selected == "Toronto")
    setPreset("512","512","43.653233","-79.383177","11");
  else if(selected == "Seneca")
    setPreset("512","512","43.771156","-79.500216","17");
}

private void setPreset(String ht, String wd, String lat, String lon, String zm){
  ttfSizeH.setText(ht);
  ttfSizeW.setText(wd);
  ttfLat.setText(lat);
  ttfLon.setText(lon);
  ttfZoom.setText(zm);
}


private void emailForm(){
  JFrame frame = new JFrame("Send Email");
  
  frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
  
  JLabel sndLbl = new JLabel();
  JLabel recLbl = new JLabel();
  JButton send = new JButton();
  JPanel hldr = new JPanel();
  
  sndLbl.setText("From Name");
  recLbl.setText("To Address");
  sender.setText("");
  sender.setColumns(20);
  send.setText("Send");
  send.addActionListener(new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      sendEmail((String)sender.getText(), (String)receiver.getText());
    }
   });
  
  GridLayout grdLyt = new GridLayout(3,2);
  hldr.setLayout(grdLyt);
  hldr.add(sndLbl);
  hldr.add(sender);
  hldr.add(recLbl);
  hldr.add(receiver);
  hldr.add(send);
  
  frame.setContentPane(hldr);
  frame.pack();
  GUIUtils.centerOnScreen(frame);
  frame.setVisible(true);
}

private void sendEmail(String sender, String reciever){
  final String user = "mapsender.system@gmail.com";
  final String pswd = "jd94bfh439bsh5v";
  
  Properties props = System.getProperties();
  props.put("mail.smtp.host", "smtp.gmail.com");//HOST
  props.put("mail.smtp.user", user);
  props.put("mail.smtp.starttls.enable", "true");
  props.put("mail.smtp.auth", "true");
  props.put("mail.smtp.port", "587");

  Session session = Session.getInstance(props, new javax.mail.Authenticator() {
    protected PasswordAuthentication getPasswordAuthentication() {
      return new PasswordAuthentication(user, pswd);
    }
  });
  
  try{
    MimeMessage msg = new MimeMessage(session);
    msg.setFrom(new InternetAddress("mapsender.system@gmail.com"));
    msg.setRecipients(Message.RecipientType.TO, reciever);
    msg.setSubject("Map From " + sender);
    MimeBodyPart mbp1 = new MimeBodyPart();
    mbp1.setText(uri);
    MimeBodyPart mbp2 = new MimeBodyPart();
    File file = new File("temp.png");
    if(_img != null){
      try{
        ImageIO.write(_img, "png", file);
        mbp2.attachFile(file);
      }catch(IOException e) {
        System.out.println("Error - Saving Image");
      }
    }
    Multipart mp = new MimeMultipart();
    mp.addBodyPart(mbp1);
    mp.addBodyPart(mbp2);
    msg.setContent(mp);
    Transport.send(msg);
    file.delete();
  }catch (MessagingException mex){
    System.out.println("send failed: " + mex);
  }
}
/**<- ADDED CODE**/

public SampleApp() {
  initComponents();
  doInit();
}

private void quitProgram() {
  _task.shutdown();
  System.exit(0);
}
private void enableZoom(){
 if(_img != null){
  btnZoomPlus.setEnabled(true);
  btnZoomMinus.setEnabled(true);
 }
}
private void initComponents() {
  /**ADDED CODE ->**/
  btnSavImg = new JButton();
  cmbPresets = new JComboBox();
  cmbLabel = new JLabel();
  btnSndEml = new JButton();
  sender = new JTextField();
  receiver = new JTextField();
  sveeml = new JPanel();
  /**<- ADDED CODE**/
  
/**created by herman wu**/
btnZoomPlus = new JButton(" + ");
btnZoomMinus = new JButton(" - ");
btnZoomPlus.setEnabled(false);
btnZoomMinus.setEnabled(false);
btnZoomPlus.addActionListener(new ActionListener() {
    public void actionPerformed(ActionEvent e) {
     if (e.getSource() == btnZoomPlus){
      curZoom++;
     }else if (e.getSource() == btnZoomMinus){
      curZoom--;
     }
     startTaskAction();
}
});
btnZoomMinus.addActionListener(new ActionListener() {
    public void actionPerformed(ActionEvent e) {
     if (e.getSource() == btnZoomMinus){
      curZoom--;
     }
     startTaskAction();
}
});
zpl = new JPanel();

  
  /** JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents**/
  /** Generated using JFormDesigner non-commercial license**/
  dialogPane = new JPanel();
  contentPanel = new JPanel();
  panel1 = new JPanel();
  label2 = new JLabel();
  ttfSizeW = new JTextField();
  label4 = new JLabel();
  ttfLat = new JTextField();
  btnGetMap = new JButton();
  label3 = new JLabel();
  ttfSizeH = new JTextField();
  label5 = new JLabel();
  ttfLon = new JTextField();
  btnQuit = new JButton();
  /**label1 = new JLabel();**/
  ttfLicense = new JTextField();
  label6 = new JLabel();
  ttfZoom = new JTextField();
  curZoom = 10;
  scrollPane1 = new JScrollPane();
  ttaStatus = new JTextArea();
  panel2 = new JPanel();
  panel3 = new JPanel();
  checkboxRecvStatus = new JCheckBox();
  checkboxSendStatus = new JCheckBox();
  ttfProgressMsg = new JTextField();
  progressBar = new JProgressBar();
  lblProgressStatus = new JLabel();

  /**======== this ========**/
  setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
  setTitle("Google Static Maps");
  setIconImage(null);
  Container contentPane = getContentPane();
  contentPane.setLayout(new BorderLayout());

  /**======== dialogPane ========**/
  {
   dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
   dialogPane.setOpaque(false);
   dialogPane.setLayout(new BorderLayout());

   /**======== contentPanel ========**/
   {
    contentPanel.setOpaque(false);
    contentPanel.setLayout(new TableLayout(new double[][] {
     {TableLayout.FILL},
     {TableLayout.PREFERRED, TableLayout.FILL, TableLayout.PREFERRED}}));
    ((TableLayout)contentPanel.getLayout()).setHGap(5);
    ((TableLayout)contentPanel.getLayout()).setVGap(5);

    /**======== panel1 ========**/
    {
     panel1.setOpaque(false);
     panel1.setBorder(new CompoundBorder(
      new TitledBorder("Configure the inputs to Google Static Maps"),
      Borders.DLU2_BORDER));
     panel1.setLayout(new TableLayout(new double[][] {
      {0.17, 0.17, 0.17, 0.17, 0.05, TableLayout.FILL},
      {TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED}}));
     ((TableLayout)panel1.getLayout()).setHGap(5);
     ((TableLayout)panel1.getLayout()).setVGap(5);

     /**---- label2 ----**/
     label2.setText("Size Width");
     label2.setHorizontalAlignment(SwingConstants.RIGHT);
     panel1.add(label2, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- ttfSizeW ----**/
     ttfSizeW.setText("512");
     panel1.add(ttfSizeW, new TableLayoutConstraints(1, 0, 1, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- label4 ----**/
     label4.setText("Latitude");
     label4.setHorizontalAlignment(SwingConstants.RIGHT);
     panel1.add(label4, new TableLayoutConstraints(2, 0, 2, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- ttfLat ----**/
     ttfLat.setText("38.931099");
     panel1.add(ttfLat, new TableLayoutConstraints(3, 0, 3, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- btnGetMap ----**/
     btnGetMap.setText("Get Map");
     btnGetMap.setHorizontalAlignment(SwingConstants.LEFT);
     btnGetMap.setMnemonic('G');
     btnGetMap.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
       startTaskAction();
      }
     });
     panel1.add(btnGetMap, new TableLayoutConstraints(5, 0, 5, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
     
     /**ADDED CODE ->**/
     GridLayout grdLyt = new GridLayout(1,2);
     sveeml.setLayout(grdLyt);
   /**---- btnSavImg ----  **/
     btnSavImg.setText("Save Image");
     btnSavImg.setEnabled(false);
     btnSavImg.setHorizontalAlignment(SwingConstants.LEFT);
     btnSavImg.setMnemonic('G');
     btnSavImg.addActionListener(new ActionListener() {
       public void actionPerformed(ActionEvent e) {
         saveImageSys();
       }
     });     
     sveeml.add(btnSavImg);
     initSaveDiag();
     
   /**---- btnSndEml ----  **/
     btnSndEml.setText("Email Map");
     btnSndEml.setEnabled(false);
     btnSndEml.setHorizontalAlignment(SwingConstants.LEFT);
     btnSndEml.setMnemonic('G');
     btnSndEml.addActionListener(new ActionListener() {
       public void actionPerformed(ActionEvent e) {
         emailForm();
       }
     });     
     sveeml.add(btnSndEml);
     panel1.add(sveeml, new TableLayoutConstraints(5, 2, 5, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
   /**---- cmbLabel ----**/
     cmbLabel.setText("Presets");
     cmbLabel.setHorizontalAlignment(SwingConstants.RIGHT);
     panel1.add(cmbLabel, new TableLayoutConstraints(2, 2, 2, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- cmbPresets() ----**/
     crtPrstList();
     cmbPresets.addActionListener(new ActionListener() {
       public void actionPerformed(ActionEvent e) {
         changeSet();
       }
     });  
     panel1.add(cmbPresets, new TableLayoutConstraints(3, 2, 3, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
     /**<- ADDED CODE**/
     
     /**---- label3 ----**/
     label3.setText("Size Height");
     label3.setHorizontalAlignment(SwingConstants.RIGHT);
     panel1.add(label3, new TableLayoutConstraints(0, 1, 0, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- ttfSizeH ----**/
     ttfSizeH.setText("512");
     panel1.add(ttfSizeH, new TableLayoutConstraints(1, 1, 1, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- label5 ----**/
     label5.setText("Longitude");
     label5.setHorizontalAlignment(SwingConstants.RIGHT);
     panel1.add(label5, new TableLayoutConstraints(2, 1, 2, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- ttfLon ----**/
     ttfLon.setText("-77.3489");
     panel1.add(ttfLon, new TableLayoutConstraints(3, 1, 3, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- btnQuit ----**/
     btnQuit.setText("Quit");
     btnQuit.setMnemonic('Q');
     btnQuit.setHorizontalAlignment(SwingConstants.LEFT);
     btnQuit.setHorizontalTextPosition(SwingConstants.RIGHT);
     btnQuit.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
       quitProgram();
      }
     });
     panel1.add(btnQuit, new TableLayoutConstraints(5, 1, 5, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- label1 ----
     *label1.setText("License Key");
     *label1.setHorizontalAlignment(SwingConstants.RIGHT);
     *panel1.add(label1, new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
     *
     *---- ttfLicense ----
     *ttfLicense.setToolTipText("Enter your own URI for a file to download in the background");
     *panel1.add(ttfLicense, new TableLayoutConstraints(1, 2, 1, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
      **/
     /**---- label6 ----**/
     label6.setText("Zoom");
     label6.setHorizontalAlignment(SwingConstants.RIGHT);
     panel1.add(label6, new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- ttfZoom ----**/
     /**ttfZoom.setText("14");**/
     GridLayout zg = new GridLayout(1,2);
     zpl.setLayout(zg);
     zpl.add(btnZoomPlus);
     zpl.add(btnZoomMinus);
     panel1.add(zpl , new TableLayoutConstraints(1, 2, 1, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
    
    }
    contentPanel.add(panel1, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

    /**======== scrollPane1 ========**/
    {
     scrollPane1.setBorder(new TitledBorder("System.out - displays all status and progress messages, etc."));
     scrollPane1.setOpaque(false);

     /**---- ttaStatus ----**/
     ttaStatus.setBorder(Borders.createEmptyBorder("1dlu, 1dlu, 1dlu, 1dlu"));
     ttaStatus.setToolTipText("<html>Task progress updates (messages) are displayed here,<br>along with any other output generated by the Task.<html>");
     scrollPane1.setViewportView(ttaStatus);
    }
    contentPanel.add(scrollPane1, new TableLayoutConstraints(0, 1, 0, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

    /**======== panel2 ========**/
    {
     panel2.setOpaque(false);
     panel2.setBorder(new CompoundBorder(
      new TitledBorder("Status - control progress reporting"),
      Borders.DLU2_BORDER));
     panel2.setLayout(new TableLayout(new double[][] {
      {0.45, TableLayout.FILL, 0.45},
      {TableLayout.PREFERRED, TableLayout.PREFERRED}}));
     ((TableLayout)panel2.getLayout()).setHGap(5);
     ((TableLayout)panel2.getLayout()).setVGap(5);

     /**======== panel3 ========**/
     {
      panel3.setOpaque(false);
      panel3.setLayout(new GridLayout(1, 2));

      /**---- checkboxRecvStatus ----**/
      checkboxRecvStatus.setText("Enable \"Recieve\"");
      checkboxRecvStatus.setOpaque(false);
      checkboxRecvStatus.setToolTipText("Task will fire \"send\" status updates");
      checkboxRecvStatus.setSelected(true);
      panel3.add(checkboxRecvStatus);

      /**---- checkboxSendStatus ----**/
      checkboxSendStatus.setText("Enable \"Send\"");
      checkboxSendStatus.setOpaque(false);
      checkboxSendStatus.setToolTipText("Task will fire \"recieve\" status updates");
      panel3.add(checkboxSendStatus);
     }
     panel2.add(panel3, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- ttfProgressMsg ----**/
     ttfProgressMsg.setText("Loading map from Google Static Maps");
     ttfProgressMsg.setToolTipText("Set the task progress message here");
     panel2.add(ttfProgressMsg, new TableLayoutConstraints(2, 0, 2, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- progressBar ----**/
     progressBar.setStringPainted(true);
     progressBar.setString("progress %");
     progressBar.setToolTipText("% progress is displayed here");
     panel2.add(progressBar, new TableLayoutConstraints(0, 1, 0, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

     /**---- lblProgressStatus ----**/
     lblProgressStatus.setText("task status listener");
     lblProgressStatus.setHorizontalTextPosition(SwingConstants.LEFT);
     lblProgressStatus.setHorizontalAlignment(SwingConstants.LEFT);
     lblProgressStatus.setToolTipText("Task status messages are displayed here when the task runs");
     panel2.add(lblProgressStatus, new TableLayoutConstraints(2, 1, 2, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
    }
    contentPanel.add(panel2, new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
   }
   dialogPane.add(contentPanel, BorderLayout.CENTER);
  }
  contentPane.add(dialogPane, BorderLayout.CENTER);
  setSize(675, 485);
  setLocationRelativeTo(null);
  /** JFormDesigner - End of component initialization  //GEN-END:initComponents**/
}

/**ADDED CODE ->**/
private JButton btnSavImg;
private JComboBox cmbPresets;
private JLabel cmbLabel;
private JButton btnSndEml;
private JTextField sender;
private JTextField receiver;
private JPanel sveeml;
/**<- ADDED CODE**/

/**created by herman wu**/
private JButton btnZoomPlus, btnZoomMinus;
private JPanel zpl;

/** JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
 * Generated using JFormDesigner non-commercial license
 * 
**/
private JPanel dialogPane;
private JPanel contentPanel;
private JPanel panel1;
private JLabel label2;
private JTextField ttfSizeW;
private JLabel label4;
private JTextField ttfLat;
private JButton btnGetMap;
private JLabel label3;
private JTextField ttfSizeH;
private JLabel label5;
private JTextField ttfLon;
private JButton btnQuit;
/**private JLabel label1;**/
private JTextField ttfLicense;
private JLabel label6;
private JTextField ttfZoom;
private JScrollPane scrollPane1;
private JTextArea ttaStatus;
private JPanel panel2;
private JPanel panel3;
private JCheckBox checkboxRecvStatus;
private JCheckBox checkboxSendStatus;
private JTextField ttfProgressMsg;
private JProgressBar progressBar;
private JLabel lblProgressStatus;
/** JFormDesigner - End of variables declaration  //GEN-END:variables**/
}
