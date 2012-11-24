package me.asofold.bpl.testncp.utils;

/**
 * Convenient argument parsing.<br>
 * 
 * Format is: key(=|+|-)value<br>
 * value is a double value, key will be trim+lower case, = indicates setting a value, + and - will add or subtract to the reference value, which depends on the context.<br>
 * 
 * @author mc_dev
 *
 */
public class DoubleDef {
	public final boolean add;
	public final double value;
	public final String key;
	public DoubleDef(String arg){
		final char op;
		if (arg.indexOf('=') != -1)			op = '=';
		else if (arg.indexOf('+') != -1) 	op = '+';
		else if (arg.indexOf('-') != -1) 	op = '-';
		else throw new NumberFormatException("key(+|-|=)value");
		int index = arg.indexOf(op);
		double value = parseDouble(arg.substring(index + 1));
		key =  arg.substring(0, index).trim().toLowerCase();
		if (op == '='){
			add = false;
			this.value = value;
		}
		else{
			add = true;
			if (op == '+') this.value = value;
			else this.value = -value;
		}
		
	}
	
	private double parseDouble(String arg) {
		arg = arg.toUpperCase();
		if (arg.equals("NAN")) return Double.NaN;
		else if (arg.equals("MAX")) return Double.MAX_VALUE;
		else if (arg.equals("MIN")) return Double.MIN_VALUE;
		else if (arg.equals("NEGINF")) return Double.NEGATIVE_INFINITY;
		else if (arg.equals("POSINF")) return Double.POSITIVE_INFINITY;
		else return Double.parseDouble(arg);
	}
	
	public double apply(double context){
		if (add) return context + value;
		else return value;
	}
}
