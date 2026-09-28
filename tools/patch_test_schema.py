from pathlib import Path

SERVICE = Path('app/src/main/java/com/codybot/prototype/CodyAccessibilityService.java')
s = SERVICE.read_text()

if 'schemaTestTargetPackage' not in s:
    s = s.replace(
        'private static volatile String lastTargetPackage="";',
        'private static volatile String lastTargetPackage=""; private static volatile String schemaTestTargetPackage="";'
    )

getter = 'public static String getLastTargetPackage(){return lastTargetPackage;}'
if 'getSchemaTestTargetPackage' not in s:
    s = s.replace(
        getter,
        getter + ' public static String getSchemaTestTargetPackage(){return schemaTestTargetPackage;}'
    )

old_test = 'private void testSchemaCalibration(int length){\n stopCompilation();hideMainScanOverlay();'
new_test = '''private void testSchemaCalibration(int length){
 String fg=lastTargetPackage;
 if(fg!=null&&!fg.isEmpty()&&!fg.equals(getPackageName()))schemaTestTargetPackage=fg;
 ensureDefaultSchema9();
 stopCompilation();hideMainScanOverlay();'''
if old_test in s:
    s = s.replace(old_test, new_test, 1)
elif 'ensureDefaultSchema9();' not in s:
    raise SystemExit('testSchemaCalibration pattern not found')

if 'DEFAULT_SCHEMA_9' not in s:
    default_method = '''private static final int[][] DEFAULT_SCHEMA_9 = new int[][]{
 {83,422,212,406,321,428,438,419,550,415,656,414,778,418,879,424,1009,422},
 {100,556,207,557,323,564,451,555,560,557,652,549,760,550,901,550,980,553},
 {101,681,205,688,324,697,446,682,534,685,650,682,765,689,905,684,1003,661},
 {107,808,204,799,320,807,423,806,536,789,657,802,773,799,875,802,1010,816},
 {106,792,223,808,324,808,452,796,565,805,661,804,758,800,879,810,1005,803},
 {108,795,210,800,326,812,444,805,552,807,646,812,770,796,874,804,987,796},
 {106,787,214,808,322,815,447,785,555,812,657,808,765,799,884,812,1002,815},
 {80,808,214,807,306,804,456,794,544,803,671,811,774,802,875,811,987,802},
 {101,795,202,805,333,809,435,808,553,816,645,815,790,812,883,821,978,809},
 {113,801,210,797,326,805,447,788,565,795,658,807,788,810,890,814,1004,809},
 {106,899,217,942,324,920,429,918,552,918,651,931,785,918,895,921,1000,917},
 {101,1036,216,1043,325,1055,439,1038,564,1039,649,1038,784,1036,889,1051,1006,1022},
 {126,1176,209,1165,332,1188,441,1163,558,1177,676,1191,773,1170,883,1186,978,1168}
};
private void ensureDefaultSchema9(){
 android.content.SharedPreferences p=getSharedPreferences("codybot_schema",MODE_PRIVATE);
 android.content.SharedPreferences.Editor e=p.edit();
 boolean changed=false;
 for(int r=1;r<=DEFAULT_SCHEMA_9.length;r++){
  if(p.getString("row_"+r+"_9",null)==null){
   StringBuilder row=new StringBuilder();
   for(int i=0;i<9;i++){if(i>0)row.append(',');row.append(DEFAULT_SCHEMA_9[r-1][i*2]).append(',').append(DEFAULT_SCHEMA_9[r-1][i*2+1]);}
   e.putString("row_"+r+"_9",row.toString()); changed=true;
   if(r==1)e.putString("centers_9",row.toString());
  }
 }
 if(p.getInt("rows_9",0)<13){e.putInt("rows_9",13);changed=true;}
 if(changed)e.apply();
 android.content.SharedPreferences v3=getSharedPreferences("codybot_schema_v3",MODE_PRIVATE);
 if(v3.getString("schema_9",null)==null){
  StringBuilder out=new StringBuilder("CODYBOT_SCHEMA_V3|9\\n");
  for(int r=1;r<=DEFAULT_SCHEMA_9.length;r++){
   out.append("ROW|").append(r).append('|');
   for(int i=0;i<9;i++){if(i>0)out.append(';');out.append(DEFAULT_SCHEMA_9[r-1][i*2]).append(',').append(DEFAULT_SCHEMA_9[r-1][i*2+1]);}
   out.append('\\n');
  }
  v3.edit().putString("schema_9",out.toString()).apply();
 }
}
'''
    marker = 'private int[] getSchemaRowCenters(int row,int length){'
    if marker not in s:
        raise SystemExit('getSchemaRowCenters marker not found')
    s = s.replace(marker, default_method + marker, 1)

old_get = 'private int[] getSchemaRowCenters(int row,int length){if(row<1||length<1)return null;android.content.SharedPreferences p=getSharedPreferences("codybot_schema",MODE_PRIVATE);String raw=p.getString("row_"+row+"_"+length,null);if(raw==null&&row==1)raw=p.getString("centers_"+length,null);if(raw==null)return null;String[] a=raw.split(",");if(a.length!=length*2)return null;try{int[] c=new int[a.length];for(int i=0;i<a.length;i++)c[i]=Integer.parseInt(a[i].trim());return c;}catch(Exception e){return null;}}'
new_get = '''private int[] getSchemaRowCenters(int row,int length){
 if(row<1||length<1)return null;
 android.content.SharedPreferences p=getSharedPreferences("codybot_schema",MODE_PRIVATE);
 String raw=p.getString("row_"+row+"_"+length,null);
 if(raw==null&&length==9&&row>=1&&row<=DEFAULT_SCHEMA_9.length){int[] d=DEFAULT_SCHEMA_9[row-1];return d.clone();}
 if(raw==null&&row==1)raw=p.getString("centers_"+length,null);
 if(raw==null)return null;
 String[] a=raw.split(",");if(a.length!=length*2)return null;
 try{int[] c=new int[a.length];for(int i=0;i<a.length;i++)c[i]=Integer.parseInt(a[i].trim());return c;}catch(Exception e){return null;}
}'''
if old_get in s:
    s = s.replace(old_get, new_get, 1)
else:
    raise SystemExit('getSchemaRowCenters body not found')

SERVICE.write_text(s)

MP = Path('app/src/main/java/com/codybot/prototype/MediaProjectionActivity.java')
m = MP.read_text()
old = 'final String target = CodyAccessibilityService.getLastTargetPackage();'
new = 'String target = CodyAccessibilityService.getSchemaTestTargetPackage(); if(target==null||target.isEmpty()||target.equals(getPackageName())) target=CodyAccessibilityService.getLastTargetPackage();'
if old in m:
    m = m.replace(old, new, 1)
MP.write_text(m)
