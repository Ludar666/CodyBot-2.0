package com.codybot.prototype;

import com.codybot.app.CodyAccessibilityService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.InputType;
import java.util.Map;

public class MainActivity extends Activity {
    private TextView status;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(50,50,50,50);
        TextView title=new TextView(this); title.setText("CodyBot 3.1"); title.setTextSize(26); layout.addView(title);
        status=new TextView(this); status.setTextSize(16); status.setPadding(0,30,0,30); layout.addView(status);
        Button overlayButton=new Button(this); overlayButton.setText("1. ATTIVA SOVRAPPOSIZIONE"); overlayButton.setOnClickListener(v->openOverlaySettings()); layout.addView(overlayButton);
        Button accessibilityButton=new Button(this); accessibilityButton.setText("2. ATTIVA ACCESSIBILITÀ"); accessibilityButton.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}); layout.addView(accessibilityButton);
        Button advancedButton=new Button(this); advancedButton.setText("3. STRUMENTI AVANZATI"); advancedButton.setOnClickListener(v->showAdvancedTools()); layout.addView(advancedButton);
        Button closeButton=new Button(this); closeButton.setText("CHIUDI CODYBOT"); closeButton.setOnClickListener(v->{try{sendBroadcast(new Intent("com.codybot.HIDE_OVERLAY")); Intent s=new Intent(this,ScreenCaptureService.class);s.setAction(ScreenCaptureService.ACTION_PAUSE_CAPTURE);startService(s);}catch(Exception ignored){} finishAffinity();}); layout.addView(closeButton);
        setContentView(layout); refreshStatus();
    }
    private void showAdvancedTools(){
        final String[] options={"🔧 CALIBRA TASTIERA","🧪 TEST TASTIERA","📤 ESPORTA CALIBRAZIONE","📥 IMPORTA CALIBRAZIONE","📐 CALIBRA SCHEMA","🧪 TEST SCHEMA","📤 ESPORTA SCHEMA","📥 IMPORTA SCHEMI","📍 RILEVA COORDINATE AVANZAMENTO","🧪 TEST PUNTO AVANZAMENTO","✏️ MODIFICA COORDINATE AVANZAMENTO"};
        new AlertDialog.Builder(this).setTitle("Strumenti avanzati").setItems(options,(dialog,which)->{
            if(which==0){Intent i=new Intent("com.codybot.CALIBRATE_KEYBOARD");i.setPackage(getPackageName());sendBroadcast(i);status.setText("CALIBRAZIONE AVVIATA\n\nTocca A-Z sulla tastiera CodyCross.");}
            else if(which==1){Intent i=new Intent("com.codybot.ARM_TEST_KEYBOARD");i.setPackage(getPackageName());sendBroadcast(i);status.setText("🧪 TEST TASTIERA PRONTO\n\nPassa ora a CodyCross.\nIl test A-Z partirà automaticamente quando CodyCross diventa l'app in primo piano.");}
            else if(which==2)exportCalibration(); else if(which==3)importKeyboardCalibration(); else if(which==4)chooseMultiRowSchemaLength(); else if(which==5)chooseSchemaLength("TEST SCHEMA","TEST_SCHEMA"); else if(which==6)exportSchemaCalibration(); else if(which==7)importSchemaCalibration(); else if(which==8){Intent i=new Intent("com.codybot.DETECT_ADVANCE_COORDINATES");i.setPackage(getPackageName());sendBroadcast(i);status.setText("📍 RILEVA COORDINATE AVVIATA");} else if(which==9){Intent i=new Intent("com.codybot.TEST_ADVANCE_POINT");i.setPackage(getPackageName());sendBroadcast(i);status.setText("🧪 TEST PUNTO AVANZAMENTO INVIATO");} else if(which==10)editAdvancePoint();
        }).show();
    }
    private void editAdvancePoint(){android.content.SharedPreferences p=getSharedPreferences("codybot_advance",MODE_PRIVATE);EditText ex=new EditText(this);ex.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);ex.setHint("X");ex.setText(String.valueOf(Math.round(p.getFloat("x",1016f))));EditText ey=new EditText(this);ey.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);ey.setHint("Y");ey.setText(String.valueOf(Math.round(p.getFloat("y",1559f))));LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(30,0,30,0);box.addView(ex);box.addView(ey);new AlertDialog.Builder(this).setTitle("Coordinata avanzamento riga").setMessage("Valori predefiniti: X 1016, Y 1559").setView(box).setPositiveButton("SALVA",(d,w)->{try{float x=Float.parseFloat(ex.getText().toString()),y=Float.parseFloat(ey.getText().toString());if(x<0||y<0)throw new Exception();p.edit().putFloat("x",x).putFloat("y",y).apply();status.setText("✅ COORDINATE SALVATE\nX = "+Math.round(x)+"\nY = "+Math.round(y));}catch(Exception e){status.setText("❌ Coordinate non valide");}}).setNegativeButton("ANNULLA",null).show();}
    private void chooseMultiRowSchemaLength(){final String[] lengths=new String[18];for(int i=0;i<18;i++)lengths[i]=String.valueOf(i+3);new AlertDialog.Builder(this).setTitle("CALIBRAZIONE SCHEMA").setSingleChoiceItems(lengths,-1,(d,w)->{int len=w+3;d.dismiss();Intent i=new Intent(this,SchemaCalibrationService.class);i.setAction(SchemaCalibrationService.ACTION_START);i.putExtra(SchemaCalibrationService.EXTRA_LENGTH,len);startService(i);status.setText("📐 CALIBRAZIONE SCHEMA AVVIATA\n\n"+len+" lettere per parola.\nIl numero di parole è variabile.");}).show();}
    private void importKeyboardCalibration(){ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cb==null||!cb.hasPrimaryClip()){new AlertDialog.Builder(this).setTitle("Importa calibrazione").setMessage("Copia prima negli appunti il testo esportato.").setPositiveButton("OK",null).show();return;}String raw=cb.getPrimaryClip().getItemAt(0).coerceToText(this).toString();EditText e=new EditText(this);e.setText(raw);e.setTextSize(14);e.setGravity(android.view.Gravity.TOP);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);e.setMinLines(12);new AlertDialog.Builder(this).setTitle("Importa calibrazione tastiera").setMessage("Puoi modificare liberamente le coordinate prima di salvarle.").setView(e).setPositiveButton("SALVA",(d,w)->{Intent i=new Intent("com.codybot.IMPORT_KEYBOARD");i.setPackage(getPackageName());i.putExtra("data",e.getText().toString());sendBroadcast(i);}).setNegativeButton("ANNULLA",null).show();}
    private void importSchemaCalibration(){ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cb==null||!cb.hasPrimaryClip()){new AlertDialog.Builder(this).setTitle("Importa schemi").setMessage("Copia prima negli appunti il testo esportato.").setPositiveButton("OK",null).show();return;}String raw=cb.getPrimaryClip().getItemAt(0).coerceToText(this).toString();EditText e=new EditText(this);e.setText(raw);e.setTextSize(14);e.setGravity(android.view.Gravity.TOP);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);e.setMinLines(14);new AlertDialog.Builder(this).setTitle("Importa schemi").setMessage("Formato CODYBOT_SCHEMA_V3. Puoi modificare le coordinate prima di salvarle.").setView(e).setPositiveButton("SALVA",(d,w)->saveImportedSchemaV3(e.getText().toString())).setNegativeButton("ANNULLA",null).show();}
    private void saveImportedSchemaV3(String raw){
        try{
            String[] lines=raw.split("\\r?\\n");
            String current=null;
            int rows=0;
            java.util.LinkedHashMap<Integer,java.util.List<int[]>> parsed=new java.util.LinkedHashMap<>();

            for(String line:lines){
                String t=line.trim();
                if(t.isEmpty())continue;
                if(t.startsWith("CODYBOT_SCHEMA_V3|")){
                    current=t.substring(t.indexOf('|')+1).trim();
                    if(current.length()==0)throw new IllegalArgumentException();
                }else if(t.startsWith("ROW|")){
                    if(current==null)throw new IllegalArgumentException();
                    String[] p=t.split("\\|",3);
                    if(p.length!=3)throw new IllegalArgumentException();
                    int rowNumber=Integer.parseInt(p[1]);
                    int expected=Integer.parseInt(current);
                    if(rowNumber<1||expected<1||p[2].trim().isEmpty())throw new IllegalArgumentException();
                    String[] cells=p[2].split(";");
                    if(cells.length!=expected)throw new IllegalArgumentException();
                    java.util.List<int[]> row=new java.util.ArrayList<>();
                    for(String cell:cells){
                        String[] xy=cell.trim().split(",");
                        if(xy.length!=2)throw new IllegalArgumentException();
                        row.add(new int[]{Math.round(Float.parseFloat(xy[0].trim())),Math.round(Float.parseFloat(xy[1].trim()))});
                    }
                    parsed.put(rowNumber,row);
                    rows++;
                }
            }
            if(rows==0||current==null)throw new IllegalArgumentException();

            int cols=Integer.parseInt(current);
            java.util.Map<Integer,java.util.List<int[]>> normalized=normalizeSchemaRows(parsed,cols);
            StringBuilder out=new StringBuilder("CODYBOT_SCHEMA_V3|").append(cols).append('\n');
            for(java.util.Map.Entry<Integer,java.util.List<int[]>> e:normalized.entrySet()){
                out.append("ROW|").append(e.getKey()).append('|');
                java.util.List<int[]> row=e.getValue();
                for(int i=0;i<row.size();i++){
                    if(i>0)out.append(';');
                    out.append(row.get(i)[0]).append(',').append(row.get(i)[1]);
                }
                out.append('\n');
            }
            getSharedPreferences("codybot_schema_v3",MODE_PRIVATE).edit().putString("schema_"+cols,out.toString()).apply();
            status.setText("✅ SCHEMA V3 IMPORTATO\n"+normalized.size()+" righe regolarizzate\n"+cols+" caselle per parola");
        }catch(Exception ex){
            new AlertDialog.Builder(this).setTitle("Importazione non valida").setMessage("Il formato non è valido. Servono righe ROW con lo stesso numero di caselle.").setPositiveButton("OK",null).show();
        }
    }

    private java.util.Map<Integer,java.util.List<int[]>> normalizeSchemaRows(java.util.Map<Integer,java.util.List<int[]>> source,int cols){
        java.util.Map<Integer,java.util.List<int[]>> out=new java.util.LinkedHashMap<>();
        if(source==null||source.isEmpty()||cols<=0)return out;

        double[] medianX=new double[cols];
        for(int col=0;col<cols;col++){
            java.util.ArrayList<Integer> xs=new java.util.ArrayList<>();
            for(java.util.List<int[]> row:source.values()){
                if(row!=null&&row.size()==cols&&row.get(col)!=null)xs.add(row.get(col)[0]);
            }
            medianX[col]=medianInt(xs);
        }

        double sumI=0,sumX=0,sumII=0,sumIX=0;
        for(int i=0;i<cols;i++){
            sumI+=i; sumX+=medianX[i]; sumII+=(double)i*i; sumIX+=i*medianX[i];
        }
        double denom=cols*sumII-sumI*sumI;
        double spacing=denom==0?0:(cols*sumIX-sumI*sumX)/denom;
        double first=cols==0?0:(sumX-spacing*sumI)/cols;
        if(spacing<1)spacing=cols>1?medianSpacing(medianX):1;

        for(java.util.Map.Entry<Integer,java.util.List<int[]>> e:source.entrySet()){
            java.util.List<int[]> row=e.getValue();
            if(row==null||row.size()!=cols)continue;
            java.util.ArrayList<Integer> ys=new java.util.ArrayList<>();
            for(int[] p:row)if(p!=null&&p.length>=2)ys.add(p[1]);
            int y=(int)Math.round(medianInt(ys));
            java.util.List<int[]> nr=new java.util.ArrayList<>();
            for(int col=0;col<cols;col++)nr.add(new int[]{(int)Math.round(first+spacing*col),y});
            out.put(e.getKey(),nr);
        }
        return out;
    }

    private double medianInt(java.util.List<Integer> values){
        if(values==null||values.isEmpty())return 0;
        java.util.ArrayList<Integer> v=new java.util.ArrayList<>(values);
        java.util.Collections.sort(v);
        int n=v.size();
        return n%2==1?v.get(n/2):(v.get(n/2-1)+v.get(n/2))/2.0;
    }

    private double medianSpacing(double[] x){
        if(x==null||x.length<2)return 1;
        java.util.ArrayList<Integer> d=new java.util.ArrayList<>();
        for(int i=1;i<x.length;i++){
            int delta=(int)Math.round(x[i]-x[i-1]);
            if(delta>10)d.add(delta);
        }
        return d.isEmpty()?1:medianInt(d);
    }

    private void exportSchemaCalibration(){Map<String,?> all=getSharedPreferences("codybot_schema_v3",MODE_PRIVATE).getAll();StringBuilder out=new StringBuilder();for(Map.Entry<String,?> e:all.entrySet()){if(!e.getKey().startsWith("schema_")||!(e.getValue() instanceof String))continue;String s=(String)e.getValue();if(!s.startsWith("CODYBOT_SCHEMA_V3|"))continue;if(out.length()>0)out.append("\n");out.append(s.trim()).append("\n");}if(out.length()==0){new AlertDialog.Builder(this).setTitle("Esporta schemi").setMessage("Nessuno schema V3 salvato. Esegui prima CALIBRA SCHEMA.").setPositiveButton("OK",null).show();return;}String text=out.toString();new AlertDialog.Builder(this).setTitle("Esporta schemi").setMessage(text).setPositiveButton("COPIA",(d,w)->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("CodyBot schemi",text));status.setText("✅ SCHEMI COPIATI NEGLI APPUNTI");}).setNegativeButton("CHIUDI",null).show();}
    private void chooseSchemaLength(String title,String action){final String[] lengths=new String[18];for(int i=0;i<18;i++)lengths[i]=String.valueOf(i+3);new AlertDialog.Builder(this).setTitle(title+" - numero lettere").setItems(lengths,(d,w)->{int len=w+3;Intent i=new Intent("com.codybot."+action);i.setPackage(getPackageName());i.putExtra("length",len);sendBroadcast(i);status.setText(title+" avviata\n\nSchema da "+len+" lettere.");}).show();}
    private void exportCalibration(){String raw=getSharedPreferences("codybot_keyboard",MODE_PRIVATE).getString("centers",null);if(raw==null||raw.trim().isEmpty()){new AlertDialog.Builder(this).setTitle("Esporta calibrazione").setMessage("Nessuna calibrazione salvata. Esegui prima CALIBRA TASTIERA.").setPositiveButton("OK",null).show();return;}String[] parts=raw.split(",");if(parts.length!=52){new AlertDialog.Builder(this).setTitle("Esporta calibrazione").setMessage("La calibrazione salvata non è valida.").setPositiveButton("OK",null).show();return;}StringBuilder sb=new StringBuilder("CodyBot - Calibrazione tastiera\n");sb.append("Schermo: ").append(getSharedPreferences("codybot_keyboard",MODE_PRIVATE).getInt("width",0)).append(" x ").append(getSharedPreferences("codybot_keyboard",MODE_PRIVATE).getInt("height",0)).append("\n\n");String letters="ABCDEFGHIJKLMNOPQRSTUVWXYZ";for(int i=0;i<26;i++)sb.append(letters.charAt(i)).append(" = ").append(Math.round(Float.parseFloat(parts[i*2]))).append(", ").append(Math.round(Float.parseFloat(parts[i*2+1]))).append("\n");String text=sb.toString();new AlertDialog.Builder(this).setTitle("Calibrazione salvata").setMessage(text).setPositiveButton("COPIA",(d,w)->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("CodyBot calibrazione",text));status.setText("✅ COORDINATE COPIATE NEGLI APPUNTI");}).setNegativeButton("CHIUDI",null).show();}
    private void openOverlaySettings(){try{if(!Settings.canDrawOverlays(this))startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
    private boolean isAccessibilityEnabled(){String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(enabled==null)return false;String expected=new ComponentName(this,CodyAccessibilityService.class).flattenToString();return enabled.contains(expected);}
    private void refreshStatus(){boolean overlay=Settings.canDrawOverlays(this);boolean accessibility=isAccessibilityEnabled();status.setText("Sovrapposizione: "+(overlay?"ATTIVA ✓":"NON ATTIVA ✗")+"\nAccessibilità: "+(accessibility?"ATTIVA ✓":"NON ATTIVA ✗")+"\n\nPer vedere CodyBot sopra CodyCross devono essere attive ENTRAMBE.");}
    @Override protected void onResume(){super.onResume();if(status!=null)refreshStatus();}
}
