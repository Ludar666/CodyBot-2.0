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
import android.widget.ScrollView;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import java.util.Map;

public class MainActivity extends Activity {
    private TextView status;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28,28,28,24);
        root.setBackgroundColor(android.graphics.Color.rgb(18,22,28));

        TextView title=new TextView(this);
        title.setText("CodyBot");
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(null,android.graphics.Typeface.BOLD);
        title.setPadding(4,4,4,2);
        root.addView(title);

        TextView subtitle=new TextView(this);
        subtitle.setText("Pannello di controllo");
        subtitle.setTextColor(android.graphics.Color.rgb(150,165,178));
        subtitle.setTextSize(14);
        subtitle.setPadding(4,0,4,22);
        root.addView(subtitle);

        ScrollView scroll=new ScrollView(this);
        LinearLayout menu=new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);

        TextView section=new TextView(this);
        section.setText("FUNZIONI PRINCIPALI");
        section.setTextColor(android.graphics.Color.rgb(0,188,212));
        section.setTextSize(12);
        section.setTypeface(null,android.graphics.Typeface.BOLD);
        section.setPadding(4,6,4,10);
        menu.addView(section);

        menu.addView(mainMenuButton("⌨  CALIBRAZIONE TASTIERA","Calibra, testa, importa o esporta la tastiera",v->showKeyboardMenu()));
        menu.addView(mainMenuButton("▦  CALIBRAZIONE SCHEMI","Acquisisci, testa, importa o esporta gli schemi",v->showSchemaMenu()));
        menu.addView(mainMenuButton("⌖  RILEVAMENTO COORDINATE","Rileva, modifica e testa la coordinata di avanzamento",v->showAdvanceMenu()));

        TextView setup=new TextView(this);
        setup.setText("CONFIGURAZIONE");
        setup.setTextColor(android.graphics.Color.rgb(0,188,212));
        setup.setTextSize(12);
        setup.setTypeface(null,android.graphics.Typeface.BOLD);
        setup.setPadding(4,22,4,10);
        menu.addView(setup);
        menu.addView(mainMenuButton("⚙  IMPOSTAZIONI","Sovrapposizione e accessibilità",v->showSettingsMenu()));
        TextView statusTitle=new TextView(this);
        statusTitle.setText("STATO");
        statusTitle.setTextColor(android.graphics.Color.rgb(0,188,212));
        statusTitle.setTextSize(12);
        statusTitle.setTypeface(null,android.graphics.Typeface.BOLD);
        statusTitle.setPadding(4,22,4,8);
        menu.addView(statusTitle);

        status=new TextView(this);
        status.setTextColor(android.graphics.Color.rgb(205,215,222));
        status.setTextSize(14);
        status.setPadding(16,14,16,14);
        GradientDrawable statusBg=new GradientDrawable();
        statusBg.setColor(android.graphics.Color.rgb(28,34,42));
        statusBg.setCornerRadius(18);
        status.setBackground(statusBg);
        menu.addView(status,new LinearLayout.LayoutParams(-1,-2));

        scroll.addView(menu);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        Button closeButton=mainMenuButton("❌  CHIUDI CODYBOT","Chiudi CodyBot e torna alla schermata principale",v->closeCodyBot());
        LinearLayout.LayoutParams closeLp=new LinearLayout.LayoutParams(-1,112);
        closeLp.setMargins(0,0,0,0);
        root.addView(closeButton,closeLp);

        setContentView(root);
        refreshStatus();
    }

    private Button mainMenuButton(String title,String desc,android.view.View.OnClickListener listener){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18,15,18,15);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(android.graphics.Color.rgb(30,38,47));
        bg.setCornerRadius(22);
        box.setBackground(bg);
        TextView t=new TextView(this);
        t.setText(title);
        t.setTextColor(android.graphics.Color.WHITE);
        t.setTextSize(17);
        t.setTypeface(null,android.graphics.Typeface.BOLD);
        TextView d=new TextView(this);
        d.setText(desc);
        d.setTextColor(android.graphics.Color.rgb(155,170,182));
        d.setTextSize(12);
        d.setPadding(0,5,0,0);
        box.addView(t);
        box.addView(d);
        box.setOnClickListener(listener);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,12);
        box.setLayoutParams(lp);
        Button hidden=new Button(this);
        // Restituiamo un Button per mantenere il comportamento di click/accessibilità
        hidden.setVisibility(android.view.View.GONE);
        return wrapAsButton(box,listener);
    }

    private Button wrapAsButton(LinearLayout box,android.view.View.OnClickListener listener){
        Button b=new Button(this);
        b.setText("");
        b.setBackground(box.getBackground());
        b.setOnClickListener(listener);
        b.setAllCaps(false);
        b.setMinHeight(108);
        b.setPadding(18,0,18,0);
        b.setContentDescription(box.getChildAt(0) instanceof TextView ? ((TextView)box.getChildAt(0)).getText().toString() : "CodyBot");
        // Il testo descrittivo viene mostrato in una seconda riga tramite spannable.
        if(box.getChildCount()>1){
            String a=((TextView)box.getChildAt(0)).getText().toString();
            String c=((TextView)box.getChildAt(1)).getText().toString();
            b.setText(a+"\n"+c);
            b.setTextColor(android.graphics.Color.WHITE);
            b.setTextSize(15);
            b.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.LEFT);
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,112);
        lp.setMargins(0,0,0,12);
        b.setLayoutParams(lp);
        return b;
    }

    private void showKeyboardMenu(){
        final String[] options={"🔧 CALIBRA","🧪 TEST","📥 IMPORTA","📤 ESPORTA","📍 VEDI COORDINATE"};
        new AlertDialog.Builder(this).setTitle("CALIBRAZIONE TASTIERA").setItems(options,(d,w)->{
            if(w==0){
                Intent i=new Intent("com.codybot.CALIBRATE_KEYBOARD");i.setPackage(getPackageName());sendBroadcast(i);
                status.setText("CALIBRAZIONE TASTIERA AVVIATA\\n\\nTocca A-Z sulla tastiera CodyCross.");
            } else if(w==1){
                Intent i=new Intent("com.codybot.ARM_TEST_KEYBOARD");i.setPackage(getPackageName());sendBroadcast(i);
                status.setText("🧪 TEST TASTIERA PRONTO\\n\\nPassa ora a CodyCross.\\nIl test A-Z partirà automaticamente quando CodyCross diventa l'app in primo piano.");
            } else if(w==2) importKeyboardCalibration();
            else if(w==3) exportCalibration();
            else showKeyboardCoordinates();
        }).show();
    }

    private void showSchemaMenu(){
        final String[] options={"📐 CALIBRA","🧪 TEST","📥 IMPORTA","📤 ESPORTA","📍 VEDI COORDINATE"};
        new AlertDialog.Builder(this).setTitle("CALIBRAZIONE SCHEMI").setItems(options,(d,w)->{
            if(w==0) chooseMultiRowSchemaLength();
            else if(w==1) chooseSchemaLength("TEST SCHEMA","TEST_SCHEMA");
            else if(w==2) importSchemaCalibration();
            else if(w==3) exportSchemaCalibration();
            else showSchemaCoordinates();
        }).show();
    }

    private void showAdvanceMenu(){
        final String[] options={"📍 RILEVA COORDINATE","✏️ MODIFICA COORDINATE","🧪 TEST COORDINATE"};
        new AlertDialog.Builder(this).setTitle("RILEVAMENTO COORDINATE").setItems(options,(d,w)->{
            if(w==0){
                Intent i=new Intent("com.codybot.DETECT_ADVANCE_COORDINATES");i.setPackage(getPackageName());sendBroadcast(i);
                status.setText("📍 RILEVAMENTO COORDINATE AVVIATO");
            } else if(w==1) editAdvancePoint();
            else {
                Intent i=new Intent("com.codybot.TEST_ADVANCE_POINT");i.setPackage(getPackageName());sendBroadcast(i);
                status.setText("🧪 TEST COORDINATE INVIATO");
            }
        }).show();
    }

    private void showSettingsMenu(){
        final String[] options={"🪟 ATTIVA SOVRAPPOSIZIONE","♿ ATTIVA ACCESSIBILITÀ"};
        new AlertDialog.Builder(this).setTitle("IMPOSTAZIONI").setItems(options,(d,w)->{
            if(w==0) openOverlaySettings();
            else if(w==1){
                try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
                catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}
            } else {
                try{
                    sendBroadcast(new Intent("com.codybot.HIDE_OVERLAY"));
                    Intent s=new Intent(this,ScreenCaptureService.class);
                    s.setAction(ScreenCaptureService.ACTION_PAUSE_CAPTURE);
                    startService(s);
                }catch(Exception ignored){}
                finishAffinity();
            }
        }).show();
    }

    private void closeCodyBot(){
        try{sendBroadcast(new Intent("com.codybot.HIDE_OVERLAY"));}catch(Exception ignored){}
        try{
            Intent home=new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        }catch(Exception ignored){}
        try{finishAndRemoveTask();}catch(Exception ignored){finishAffinity();}
    }

    private void editAdvancePoint(){android.content.SharedPreferences p=getSharedPreferences("codybot_advance",MODE_PRIVATE);EditText ex=new EditText(this);ex.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);ex.setHint("X");ex.setText(String.valueOf(Math.round(p.getFloat("x",997f))));EditText ey=new EditText(this);ey.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);ey.setHint("Y");ey.setText(String.valueOf(Math.round(p.getFloat("y",1542f))));LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(30,0,30,0);box.addView(ex);box.addView(ey);new AlertDialog.Builder(this).setTitle("Coordinata avanzamento riga").setMessage("Valori predefiniti: X 997, Y 1542").setView(box).setPositiveButton("SALVA",(d,w)->{try{float x=Float.parseFloat(ex.getText().toString()),y=Float.parseFloat(ey.getText().toString());if(x<0||y<0)throw new Exception();p.edit().putFloat("x",x).putFloat("y",y).apply();status.setText("✅ COORDINATE SALVATE\nX = "+Math.round(x)+"\nY = "+Math.round(y));}catch(Exception e){status.setText("❌ Coordinate non valide");}}).setNegativeButton("ANNULLA",null).show();}
    private void chooseMultiRowSchemaLength(){final String[] lengths=new String[18];for(int i=0;i<18;i++)lengths[i]=String.valueOf(i+3);new AlertDialog.Builder(this).setTitle("CALIBRAZIONE SCHEMA").setSingleChoiceItems(lengths,-1,(d,w)->{int len=w+3;d.dismiss();Intent i=new Intent(this,SchemaCalibrationService.class);i.setAction(SchemaCalibrationService.ACTION_START);i.putExtra(SchemaCalibrationService.EXTRA_LENGTH,len);startService(i);status.setText("📐 CALIBRAZIONE SCHEMA AVVIATA\n\n"+len+" lettere per parola.\nIl numero di parole è variabile.");}).show();}
    private void importKeyboardCalibration(){
 EditText e=new EditText(this);
 e.setText("");
 e.setTextSize(14);
 e.setGravity(android.view.Gravity.TOP);
 e.setHint("A = X, Y\\nB = X, Y\\n...\\nZ = X, Y");
 e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
 e.setMinLines(10);
 ScrollView sv=new ScrollView(this);
 sv.setPadding(18,0,18,0);
 sv.addView(e,new ScrollView.LayoutParams(-1,420));
 new AlertDialog.Builder(this).setTitle("Importa coordinate tastiera")
   .setMessage("Incolla le 26 righe A-Z. Le coordinate vengono salvate direttamente premendo SALVA.")
   .setView(sv)
   .setPositiveButton("SALVA",(d,w)->saveImportedKeyboard(e.getText().toString()))
   .setNegativeButton("ANNULLA",null).show();
}
private void saveImportedKeyboard(String raw){
 try{
  if(raw==null||raw.trim().isEmpty())throw new IllegalArgumentException();
  String[] lines=raw.split("\\r?\\n"); float[] c=new float[52]; boolean[] seen=new boolean[26];
  for(String line:lines){
   String t=line.trim(); int eq=t.indexOf('=');
   if(eq<0)continue;
   String left=t.substring(0,eq).trim();
   if(left.length()!=1)continue;
   int p="ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(Character.toUpperCase(left.charAt(0)));
   if(p<0)continue;
   String[] xy=t.substring(eq+1).trim().split(",");
   if(xy.length<2)throw new IllegalArgumentException();
   c[p*2]=Float.parseFloat(xy[0].trim());
   c[p*2+1]=Float.parseFloat(xy[1].trim());
   seen[p]=true;
  }
  for(boolean ok:seen)if(!ok)throw new IllegalArgumentException();
  android.content.SharedPreferences p=getSharedPreferences("codybot_keyboard",MODE_PRIVATE);
  p.edit().putString("centers",joinKeyboardCoordinates(c)).putInt("width",getResources().getDisplayMetrics().widthPixels).putInt("height",getResources().getDisplayMetrics().heightPixels).apply();
  status.setText("✅ COORDINATE TASTIERA SALVATE\\n26/26 coordinate");
 }catch(Exception ex){
  status.setText("❌ COORDINATE NON SALVATE\\nFormato richiesto: A = X, Y ... Z = X, Y");
 }
}
private String joinKeyboardCoordinates(float[] c){
 StringBuilder sb=new StringBuilder();
 for(int i=0;i<c.length;i++){if(i>0)sb.append(',');sb.append(c[i]);}
 return sb.toString();
}
    private void importSchemaCalibration(){
 EditText e=new EditText(this);
 e.setText("");
 e.setTextSize(13);
 e.setGravity(android.view.Gravity.TOP);
 e.setHint("CODYBOT_SCHEMA_V3|9\\nROW|1|...\\nROW|2|...");
 e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
 ScrollView sv=new ScrollView(this);
 sv.setPadding(18,0,18,0);
 sv.addView(e,new ScrollView.LayoutParams(-1,520));
 new AlertDialog.Builder(this).setTitle("Importa coordinate schema")
   .setMessage("Incolla lo schema completo. Il campo parte vuoto e SALVA rimane sempre visibile.")
   .setView(sv)
   .setPositiveButton("SALVA",(d,w)->saveImportedSchemaV3(e.getText().toString()))
   .setNegativeButton("ANNULLA",null).show();
}
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
    private void showKeyboardCoordinates(){String raw=getSharedPreferences("codybot_keyboard",MODE_PRIVATE).getString("centers",null);if(raw==null||raw.trim().isEmpty()){new AlertDialog.Builder(this).setTitle("Coordinate tastiera").setMessage("Nessuna coordinata tastiera salvata. Esegui prima CALIBRA o IMPORTA.").setPositiveButton("OK",null).show();return;}String[] p=raw.split(",");if(p.length!=52){new AlertDialog.Builder(this).setTitle("Coordinate tastiera").setMessage("Le coordinate salvate non sono valide.").setPositiveButton("OK",null).show();return;}StringBuilder sb=new StringBuilder();String letters="ABCDEFGHIJKLMNOPQRSTUVWXYZ";for(int i=0;i<26;i++)sb.append(letters.charAt(i)).append(" = ").append(Math.round(Float.parseFloat(p[i*2]))).append(", ").append(Math.round(Float.parseFloat(p[i*2+1]))).append("\n");showCoordinateDialog("Coordinate tastiera",sb.toString());}
    private void showSchemaCoordinates(){Map<String,?> all=getSharedPreferences("codybot_schema_v3",MODE_PRIVATE).getAll();java.util.ArrayList<String> keys=new java.util.ArrayList<>();for(String k:all.keySet())if(k.startsWith("schema_")&&all.get(k) instanceof String)keys.add(k);java.util.Collections.sort(keys,(a,b)->Integer.compare(parseSchemaKey(a),parseSchemaKey(b)));if(keys.isEmpty()){new AlertDialog.Builder(this).setTitle("Coordinate schemi").setMessage("Nessuno schema V3 salvato. Esegui prima CALIBRA o IMPORTA.").setPositiveButton("OK",null).show();return;}StringBuilder sb=new StringBuilder();for(String k:keys){String v=(String)all.get(k);sb.append(v.trim()).append("\n\n");}showCoordinateDialog("Coordinate schemi",sb.toString());}
    private int parseSchemaKey(String k){try{return Integer.parseInt(k.substring("schema_".length()));}catch(Exception e){return Integer.MAX_VALUE;}}
    private void showCoordinateDialog(String title,String text){TextView tv=new TextView(this);tv.setText(text);tv.setTextColor(android.graphics.Color.WHITE);tv.setTextSize(14);tv.setTypeface(android.graphics.Typeface.MONOSPACE);tv.setPadding(24,18,24,18);ScrollView sv=new ScrollView(this);sv.setBackgroundColor(android.graphics.Color.rgb(24,29,36));sv.addView(tv,new ScrollView.LayoutParams(-1,-2));new AlertDialog.Builder(this).setTitle(title).setView(sv).setPositiveButton("CHIUDI",null).show();}
    private void exportCalibration(){String raw=getSharedPreferences("codybot_keyboard",MODE_PRIVATE).getString("centers",null);if(raw==null||raw.trim().isEmpty()){new AlertDialog.Builder(this).setTitle("Esporta calibrazione").setMessage("Nessuna calibrazione salvata. Esegui prima CALIBRA TASTIERA.").setPositiveButton("OK",null).show();return;}String[] parts=raw.split(",");if(parts.length!=52){new AlertDialog.Builder(this).setTitle("Esporta calibrazione").setMessage("La calibrazione salvata non è valida.").setPositiveButton("OK",null).show();return;}StringBuilder sb=new StringBuilder("CodyBot - Calibrazione tastiera\n");sb.append("Schermo: ").append(getSharedPreferences("codybot_keyboard",MODE_PRIVATE).getInt("width",0)).append(" x ").append(getSharedPreferences("codybot_keyboard",MODE_PRIVATE).getInt("height",0)).append("\n\n");String letters="ABCDEFGHIJKLMNOPQRSTUVWXYZ";for(int i=0;i<26;i++)sb.append(letters.charAt(i)).append(" = ").append(Math.round(Float.parseFloat(parts[i*2]))).append(", ").append(Math.round(Float.parseFloat(parts[i*2+1]))).append("\n");String text=sb.toString();new AlertDialog.Builder(this).setTitle("Calibrazione salvata").setMessage(text).setPositiveButton("COPIA",(d,w)->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("CodyBot calibrazione",text));status.setText("✅ COORDINATE COPIATE NEGLI APPUNTI");}).setNegativeButton("CHIUDI",null).show();}
    private void openOverlaySettings(){try{if(!Settings.canDrawOverlays(this))startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
    private boolean isAccessibilityEnabled(){String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(enabled==null)return false;String expected=new ComponentName(this,CodyAccessibilityService.class).flattenToString();return enabled.contains(expected);}
    private void refreshStatus(){boolean overlay=Settings.canDrawOverlays(this);boolean accessibility=isAccessibilityEnabled();status.setText("Sovrapposizione: "+(overlay?"ATTIVA ✓":"NON ATTIVA ✗")+"\nAccessibilità: "+(accessibility?"ATTIVA ✓":"NON ATTIVA ✗")+"\n\nPer vedere CodyBot sopra CodyCross devono essere attive ENTRAMBE.");}
    @Override protected void onResume(){super.onResume();if(status!=null)refreshStatus();}
}
