package io.unifiedtv.preview;
import android.content.SharedPreferences;
import java.util.*;

/** An in-memory SharedPreferences for plain JVM tests of the small Android sections (DockCapacity, HubBuilder). */
final class MapPrefs implements SharedPreferences {
    final Map<String,Object> values=new HashMap<>();
    @Override public Map<String,?> getAll(){return new HashMap<>(values);}
    @Override public String getString(String k,String d){Object v=values.get(k);return v instanceof String?(String)v:d;}
    @SuppressWarnings("unchecked") @Override public Set<String> getStringSet(String k,Set<String> d){Object v=values.get(k);return v instanceof Set?(Set<String>)v:d;}
    @Override public int getInt(String k,int d){Object v=values.get(k);return v instanceof Integer?(Integer)v:d;}
    @Override public long getLong(String k,long d){Object v=values.get(k);return v instanceof Long?(Long)v:d;}
    @Override public float getFloat(String k,float d){Object v=values.get(k);return v instanceof Float?(Float)v:d;}
    @Override public boolean getBoolean(String k,boolean d){Object v=values.get(k);return v instanceof Boolean?(Boolean)v:d;}
    @Override public boolean contains(String k){return values.containsKey(k);}
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
    @Override public Editor edit(){
        Map<String,Object> pending=new LinkedHashMap<>();Set<String> removed=new HashSet<>();boolean[] clear={false};
        return new Editor(){
            @Override public Editor putString(String k,String v){pending.put(k,v);return this;}
            @Override public Editor putStringSet(String k,Set<String> v){pending.put(k,v);return this;}
            @Override public Editor putInt(String k,int v){pending.put(k,v);return this;}
            @Override public Editor putLong(String k,long v){pending.put(k,v);return this;}
            @Override public Editor putFloat(String k,float v){pending.put(k,v);return this;}
            @Override public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
            @Override public Editor remove(String k){removed.add(k);return this;}
            @Override public Editor clear(){clear[0]=true;return this;}
            @Override public boolean commit(){if(clear[0])values.clear();for(String k:removed)values.remove(k);for(Map.Entry<String,Object> e:pending.entrySet()){if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());}return true;}
            @Override public void apply(){commit();}
        };
    }
}
