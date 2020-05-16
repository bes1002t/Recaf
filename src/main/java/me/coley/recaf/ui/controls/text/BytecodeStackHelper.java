package me.coley.recaf.ui.controls.text;

import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import me.coley.analysis.value.AbstractValue;
import me.coley.analysis.value.NullConstantValue;
import me.coley.analysis.value.UninitializedValue;
import me.coley.recaf.parse.bytecode.MethodAssembler;
import me.coley.recaf.ui.controls.IconView;
import me.coley.recaf.util.InsnUtil;
import me.coley.recaf.util.Log;
import me.coley.recaf.util.StringUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * UI component that displays values on the stack and local variable indices.
 *
 * @author Matt
 */
public class BytecodeStackHelper extends SplitPane {
	private final BytecodeEditorPane parent;
	private final ListView<Integer> locals = new ListView<>();
	private final ListView<Integer> stack = new ListView<>();
	private Frame<AbstractValue> currentFrame;
	private MethodAssembler assembler;
	private int insnIndex;

	/**
	 * @param parent
	 * 		Bytecode panel parent, contains bytecode being analyzed.
	 */
	public BytecodeStackHelper(BytecodeEditorPane parent) {
		this.parent = parent;
		getStyleClass().add("monospaced");
		getItems().addAll(locals, stack);
		setDividerPositions(0.5);
		SplitPane.setResizableWithParent(locals, Boolean.FALSE);
		locals.setCellFactory(c -> new ValueCell(true));
		stack.setCellFactory(c -> new ValueCell(false));
	}

	/**
	 * @param assembler
	 * 		Assembler to pull frames from.
	 */
	public void setMethodAssembler(MethodAssembler assembler) {
		this.assembler = assembler;
		setDisable(false);
		update();
	}

	/**
	 * Called when the method assembler has reported errors.
	 */
	public void setErrored() {
		setDisable(true);
	}

	/**
	 * Called when the user selects a line. Updates the displayed content.
	 *
	 * @param line
	 * 		Selected line.
	 */
	public void setLine(int line) {
		if (assembler == null)
			return;
		int tmp = insnIndex;
		insnIndex = -1;
		// Find most recent selected item
		while(line > 0 && insnIndex == -1) {
			AbstractInsnNode insn = assembler.getInsn(line);
			if (insn != null)
				insnIndex = InsnUtil.index(insn);
			line--;
		}
		// Update if line changed
		if (tmp != insnIndex)
			update();
	}

	private void update() {
		// No valid instruction was found
		if (insnIndex == -1)
			return;
		Frame<AbstractValue>[] frames = assembler.getFrames();
		if (insnIndex >= frames.length)
			return;
		// Update lists
		currentFrame = assembler.getFrames()[insnIndex];
		Platform.runLater(() -> {
			locals.getItems().clear();
			stack.getItems().clear();
			if (currentFrame != null) {
				for (int i = 0; i < currentFrame.getLocals(); i++)
					locals.getItems().add(i);
				for (int i = 0; i < currentFrame.getStackSize(); i++)
					stack.getItems().add(i);
			}
		});
	}

	/**
	 * Call for rendering analyzer values.
	 *
	 * @author Matt
	 */
	private class ValueCell extends ListCell<Integer> {
		private final boolean isLocal;

		public ValueCell(boolean isLocal) {
			this.isLocal = isLocal;
		}

		@Override
		protected void updateItem(Integer item, boolean empty) {
			super.updateItem(item, empty);
			if (empty) {
				setGraphic(null);
				setText(null);
			} else {
				try {
					AbstractValue value = isLocal ? currentFrame.getLocal(item) : currentFrame.getStack(item);
					StringBuilder sb = new StringBuilder();
					if (isLocal)
						sb.append(item).append(": ");
					if (value == null || value == UninitializedValue.UNINITIALIZED_VALUE) {
						setGraphic(new IconView("icons/uninitialized.png"));
						sb.append("Uninitialized");
					} else if (value instanceof NullConstantValue) {
						setGraphic(new IconView("icons/uninitialized.png"));
						sb.append("const-null");
					} else {
						setGraphic(new IconView(value.isPrimitive() ? "icons/primitive.png" : "icons/object.png"));
						Type type = value.getType();
						String simpleTypeName = StringUtil.escape(type.getClassName());
						if (simpleTypeName.contains("."))
							simpleTypeName = simpleTypeName.substring(simpleTypeName.lastIndexOf('.') + 1);
						sb.append(simpleTypeName);
						if (value.isValueResolved())
							sb.append(": ").append(StringUtil.escape(value.getValue().toString()));
					}
					setText(sb.toString());
				}catch(Throwable t) {
					setText("Error");
					Log.error(t, "Could not display {}:{}", isLocal ? "local" : "stack", item);
				}
			}
		}
	}
}