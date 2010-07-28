/*
 * Copyright (C) 2009 Saarland University
 * 
 * This file is part of Javalanche.
 * 
 * Javalanche is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Javalanche is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with Javalanche.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.unisb.cs.st.javalanche.mutation.bytecodeMutations;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.util.CheckMethodAdapter;

import de.unisb.cs.st.javalanche.mutation.adaptedMutations.bytecode.jumps.JumpsPossibilitiesMethodAdapter;
import de.unisb.cs.st.javalanche.mutation.adaptedMutations.bytecode.replace.ReplacePossibilitiesMethodAdapter;
import de.unisb.cs.st.javalanche.mutation.bytecodeMutations.arithmetic.PossibilitiesArithmeticReplaceMethodAdapter;
import de.unisb.cs.st.javalanche.mutation.bytecodeMutations.negateJumps.NegateJumpsPossibilitiesMethodAdapter;
import de.unisb.cs.st.javalanche.mutation.bytecodeMutations.removeCalls.MyAdviceAdapter;
import de.unisb.cs.st.javalanche.mutation.bytecodeMutations.removeCalls.RemoveCallsPossibilitiesMethodAdapter;
import de.unisb.cs.st.javalanche.mutation.bytecodeMutations.replaceIntegerConstant.PossibilitiesRicMethodAdapter;
import de.unisb.cs.st.javalanche.mutation.mutationPossibilities.MutationPossibilityCollector;
import de.unisb.cs.st.javalanche.mutation.properties.MutationProperties;

public class MutationsCollectorClassAdapter extends ClassAdapter {

	private String className;

	private boolean debug = true;

	private final MutationPossibilityCollector mpc;

	private Map<Integer, Integer> ricPossibilities = new HashMap<Integer, Integer>();

	private Map<Integer, Integer> arithmeticPossibilities = new HashMap<Integer, Integer>();

	private Map<Integer, Integer> negatePossibilities = new HashMap<Integer, Integer>();

	private Map<Integer, Integer> removeCallsPossibilities = new HashMap<Integer, Integer>();

	private Map<Integer, Integer> jumpsCallsPossibilities = new HashMap<Integer, Integer>();

	private Map<Integer, Integer> replaceCallsPossibilities = new HashMap<Integer, Integer>();

	public MutationsCollectorClassAdapter(ClassVisitor cv,
			MutationPossibilityCollector mpc) {
		super(cv);
		this.mpc = mpc;
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
		if (debug) {
			mv = new CheckMethodAdapter(mv);
		}
		if (!MutationProperties.IGNORE_RIC) {
			mv = new PossibilitiesRicMethodAdapter(mv, className, name, mpc,
					ricPossibilities, desc);
		}
		if (!MutationProperties.IGNORE_NEGATE_JUMPS) {
			mv = new NegateJumpsPossibilitiesMethodAdapter(mv, className, name,
					mpc,
					negatePossibilities, desc);
		}
		if (!MutationProperties.IGNORE_ARITHMETIC_REPLACE) {
			mv = new PossibilitiesArithmeticReplaceMethodAdapter(mv, className,
					name, mpc, arithmeticPossibilities, desc);
		}
		if (!MutationProperties.IGNORE_REMOVE_CALLS) {
			mv = new RemoveCallsPossibilitiesMethodAdapter(new MyAdviceAdapter(
					mv, access, name, desc), className, name, mpc,
					removeCallsPossibilities, desc);
		}
		if (!MutationProperties.IGNORE_ADAPTED_JUMPS) {
			mv = new JumpsPossibilitiesMethodAdapter(mv, className, name, mpc,
					jumpsCallsPossibilities, desc);
		}

		if (!MutationProperties.IGNORE_ADAPTED_REPLACE) {
			mv = new ReplacePossibilitiesMethodAdapter(mv, className, name,
					mpc, replaceCallsPossibilities, desc);
		}
		return mv;
	}
}
