package idontknow;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List; 
import java.util.Map;
import java.util.Set;
import java.io.*; 
import java.util.ArrayList; 
import java.util.Date;

public class ComparisonHash
{ 
	public static void Comp() 
	{
		//Global globals= Global.GetGlobals();
		//Date today = new Date();
		Dictionary<Integer, Integer> hashFiles=new Dictionary<Integer, Integer>() {

			@Override
			public Enumeration<Integer> elements() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Integer get(Object arg0) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public boolean isEmpty() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public Enumeration<Integer> keys() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Integer put(Integer arg0, Integer arg1) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Integer remove(Object arg0) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public int size() {
				// TODO Auto-generated method stub
				return 0;
			}
		};
		//Enumeration<Integer> todayFiles = hashFiles.elements();
		hashFiles.put(1, 2);
		hashFiles.put(1, 6);
		hashFiles.put(1, 5);
		hashFiles.put(1, 4);
	//	Enumeration<Integer> todayFiles = globals.hashFilesBeforeSend.elements();
		for (Enumeration r = hashFiles.elements();r.hasMoreElements();) {
			
			System.out.println(r.nextElement());
			
		}
	}
}
