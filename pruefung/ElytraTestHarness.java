import vanillaplustweaks.datapacks.ElytraVerzauberungen;
import java.util.*;
// Liest Testfaelle zeilenweise: aktuell|elytra|brust  (je "a=1,b=3"), gibt Ergebnis aus
public class Harness {
  static Map<String,Integer> p(String s){ Map<String,Integer> m=new LinkedHashMap<>(); if(s.isEmpty())return m; for(String e:s.split(",")){String[] kv=e.split("=");m.put(kv[0],Integer.parseInt(kv[1]));} return m; }
  static String f(Map<String,Integer> m){ return new TreeMap<>(m).toString(); }
  public static void main(String[] a) throws Exception {
    Scanner sc=new Scanner(System.in);
    while(sc.hasNextLine()){ String l=sc.nextLine(); String[] x=l.split("\\|",-1);
      if(x[0].equals("M")){ System.out.println(f(ElytraVerzauberungen.zusammenfuehren(p(x[1]),p(x[2])))); }
      else { var r=ElytraVerzauberungen.aufteilen(p(x[1]),p(x[2]),p(x[3])); System.out.println(f(r.elytra())+" "+f(r.brustplatte())); } }
  }
}
