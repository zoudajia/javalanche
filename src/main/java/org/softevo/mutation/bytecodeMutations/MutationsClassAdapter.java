package org.softevo.mutation.bytecodeMutations;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.util.CheckMethodAdapter;
import org.softevo.mutation.bytecodeMutations.arithmetic.ArithmeticReplaceMethodAdapter;
import org.softevo.mutation.bytecodeMutations.negateJumps.NegateJumpsMethodAdapter;
import org.softevo.mutation.bytecodeMutations.replaceIntegerConstant.RicMethodAdapter;
import org.softevo.mutation.properties.MutationProperties;

public class MutationsClassAdapter extends ClassAdapter {

	private String className;

	private Map<Integer, Integer> ricPossibilities = new HashMap<Integer, Integer>();

	private Map<Integer, Integer> arithmeticPossibilities = new HashMap<Integer, Integer>();

	private Map<Integer, Integer> negatePossibilities = new HashMap<Integer, Integer>();

	private static final boolean DEBUG = MutationProperties.DEBUG;

	public MutationsClassAdapter(ClassVisitor cv) {
		super(cv);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
		className = name;
	}

	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, final String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, desc, signature,
				exceptions);
		if (DEBUG) {
			mv = new CheckMethodAdapter(mv);
		}
		mv = new RicMethodAdapter(mv, className, name, ricPossibilities);
		mv = new NegateJumpsMethodAdapter(mv, className, name,
				negatePossibilities);
		mv = new ArithmeticReplaceMethodAdapter(mv, className, name,
				arithmeticPossibilities);
		return mv;
	}
}
