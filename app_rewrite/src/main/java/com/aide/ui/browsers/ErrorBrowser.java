//
// Decompiled by Jadx - 1313ms
//
package com.aide.ui.browsers;

import abcd.hy;
import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Keep;
import com.aide.common.AppLog;
import com.aide.common.KeyStrokeDetector;
import com.aide.common.ListAdapterBase;
import com.aide.engine.SyntaxError;
import com.aide.ui.AppCommands;
import com.aide.ui.AppFileIcons;
import com.aide.ui.GlobalKeyCommand;
import com.aide.ui.MainActivity;
import com.aide.ui.ServiceContainer;
import com.aide.ui.rewrite.R;
import com.aide.ui.util.FileSpan;
import com.aide.ui.util.FileSystem;
import com.aide.ui.views.CustomKeysListView;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import com.aide.ui.services.ErrorService;
import io.github.zeroaicy.aide.preference.ZeroAicySetting;
import android.widget.Toast;
import android.content.ClipboardManager;

public class ErrorBrowser extends LinearLayout implements a {

	private KeyStrokeDetector.KeyStrokeHandler keyStrokeHandler;

    private ErrorAdapter adapter;

    private int errorCount;
    // private int warningCount;


    @Keep
	public ErrorBrowser(Context context) {
        this(context, null);
    }

	@Keep
	public ErrorBrowser(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
		this.keyStrokeHandler = new GlobalKeyCommand(AppCommands.Zo());
		v5();
    }

    static MainActivity getMainActivity(ErrorBrowser errorBrowser) {
        return errorBrowser.getActivity();
    }

    private MainActivity getActivity() {
        return (MainActivity)getContext();
    }

    private CustomKeysListView getListView() {
        return findViewById(R.id.errorbrowserErrorList);
    }

    static KeyStrokeDetector.KeyStrokeHandler getKeyStrokeHandler(ErrorBrowser errorBrowser) {
        return errorBrowser.keyStrokeHandler;
    }

    private void v5() {
		setLayoutParams(new LayoutParams(-1, -1));
		LayoutInflater from = LayoutInflater.from(getContext());
		View errorBrowserView = from.inflate(R.layout.errorbrowser, (ViewGroup) null);
		removeAllViews();
		addView(errorBrowserView);

		View browserHeaderView = from.inflate(R.layout.browser_header, (ViewGroup) null);

		CustomKeysListView listView = getListView();
		listView.addHeaderView(browserHeaderView, null, false);

		ErrorAdapter errorAdapter = new ErrorAdapter(this, (ErrorKeyEventListener) null);
		this.adapter = errorAdapter;
		listView.setAdapter(errorAdapter);
		listView.setOnKeyEventListener(new ErrorKeyEventListener(this));
		listView.setOnItemLongClickListener(new ErrorEntryItemLongClickListener(this, listView));
		listView.setOnItemClickListener(new c(this, listView));
		Zo();

    }

    @Override
	public void DW() {
        getListView().requestFocus();
    }

    @Keep
	@Override
	public void FH() {

    }

    @Keep
	public void VH() {
		ErrorService errorService = ServiceContainer.getErrorService();
		int errorCount = errorService.gW();
		if (errorCount >= 100 
			|| this.errorCount == errorCount) {
			return;
		}
		
		Zo();
    }

	/**
	 * 将 ErrorService 中的全部错误/警告写入项目根目录的 aide-build-errors.log。
	 * 无错误时也写一行简要说明，便于确认日志是最新的。
	 */
	private void dumpErrorsToProjectRoot(ErrorService errorService, int errorCount) {
		PrintStream out = null;
		try {
			String projectRoot = ServiceContainer.getProjectService().getCurrentAppHome();
			if (projectRoot == null || projectRoot.length() == 0) {
				return;
			}
			File logFile = new File(projectRoot, "aide-build-errors.log");
			File parent = logFile.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			out = new PrintStream(new FileOutputStream(logFile, false));

			out.println("==== AIDE-Plus 构建错误日志 ====");
			out.println("生成时间: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
			out.println("错误总数: " + errorCount);
			out.println();

			int total = 0;
			for (String filePath : errorService.nw()) {
				List<SyntaxError> errors = errorService.P8(filePath);
				if (errors == null || errors.isEmpty()) {
					continue;
				}
				out.println("### 文件: " + filePath);
				for (SyntaxError syntaxError : errors) {
					if (syntaxError == null) {
						continue;
					}
					total++;
					String describe = syntaxError.j6();
					out.println("  [错误 " + total + "] 行 " + syntaxError.jw + ":" + syntaxError.fY
							+ " - 行 " + syntaxError.qp + ":" + syntaxError.k2);
					out.println("      " + (describe == null ? "" : describe));
				}
				out.println();
			}

			// 无错误时记录警告（有错误时原 UI 不显示警告，这里也保持一致）
			if (errorCount == 0 && errorService.v5 != null) {
				int warnCount = 0;
				for (String filePath : errorService.nw()) {
					SyntaxError[] warnings = errorService.v5.get(filePath);
					if (warnings == null || warnings.length == 0) {
						continue;
					}
					boolean headerWritten = false;
					for (SyntaxError syntaxError : warnings) {
						if (syntaxError == null || syntaxError.WB == 112 || !syntaxError.Zo()) {
							continue;
						}
						if (!headerWritten) {
							out.println("### 文件: " + filePath + " (警告)");
							headerWritten = true;
						}
						warnCount++;
						String describe = syntaxError.j6();
						out.println("  [警告 " + warnCount + "] 行 " + syntaxError.jw + ":" + syntaxError.fY
								+ " - 行 " + syntaxError.qp + ":" + syntaxError.k2);
						out.println("      " + (describe == null ? "" : describe));
					}
					if (headerWritten) {
						out.println();
					}
				}
				if (warnCount == 0) {
					out.println("无错误，无警告。");
				}
			}
			out.println("==== 结束 ====");
			AppLog.i("ErrorBrowser", "dumped " + total + " errors to " + logFile.getAbsolutePath());
		} catch (Throwable t) {
			AppLog.e("ErrorBrowser", "dump errors failed: " + t.getMessage());
		} finally {
			if (out != null) {
				out.close();
			}
		}
    }

	// 
	@Keep
	public void Zo() {

		CustomKeysListView listView = getListView();
		boolean hasFocus = listView.hasFocus();

		ErrorService errorService = ServiceContainer.getErrorService();
		int errorCount = errorService.gW();
		this.errorCount = errorCount;


		int browserHeaderIconId;

		String string;
		if (errorCount == 0) {
			string = getContext().getResources().getString(R.string.view_no_errors);
			browserHeaderIconId = R.drawable.project_no_errors;;
		} else {
			if (errorCount == 1) {
				string = getContext().getResources().getString(R.string.view_one_error);
			} else {
				string = getContext().getResources().getString(R.string.view_errors, this.errorCount);
			}
			browserHeaderIconId = R.drawable.project_errors;
		}

		TextView browserHeaderLabelTextView = listView.findViewById(R.id.browserHeaderLabel);
		ImageView browserHeaderIconView = listView.findViewById(R.id.browserHeaderIcon);
		ImageView browserHeaderMenuButton = listView.findViewById(R.id.browserHeaderMenuButton);

		browserHeaderIconView.setImageResource(browserHeaderIconId);
		browserHeaderLabelTextView.setText(string);
		browserHeaderMenuButton.setVisibility(View.GONE);

		ArrayList<ErrorEntry> entryList = new ArrayList<>();

		int warningCount = 0;
		// 
		boolean isEnableShowWarning = ZeroAicySetting.isEnableShowWarning();

		ArrayList<ErrorEntry> warningList = new ArrayList<>();

		for (String filePath : errorService.nw()) {
			// error SyntaxError
			List<SyntaxError> P8 = errorService.P8(filePath);

			if (!P8.isEmpty()) {
				entryList.add(new ErrorEntry(this, filePath));

				for (SyntaxError syntaxError : P8) {
					entryList.add(new ErrorEntry(this, filePath, syntaxError));
				}
			}
			if (errorCount != 0) {
				// 有错误时不显示警告⚠️
				continue;
			}
			// 显示警告⚠️
			SyntaxError[] syntaxErrors = errorService.v5.get(filePath);
			if (!isEnableShowWarning || syntaxErrors == null || syntaxErrors.length == 0) {
				continue;
			}

			for (SyntaxError syntaxError : syntaxErrors) {
				if (syntaxError == null || syntaxError.WB == 112) {
					continue;
				}
				if (syntaxError.Zo()) {
					warningCount++;
					warningList.add(new ErrorEntry(this, filePath, syntaxError));
				}
			}
			if (warningList.isEmpty()) {
				continue;
			}
			if (P8.isEmpty()) {
				entryList.add(new ErrorEntry(this, filePath));
			}
			entryList.addAll(warningList);
			warningList.clear();

		}


		// 警告数量
		// this.warningCount = warningCount;

		this.adapter.DW(entryList);

		// 把当前错误落盘到项目根目录，方便复制给 AI 分析。
		dumpErrorsToProjectRoot(errorService, errorCount);

		if (hasFocus) {
			AppLog.d("Focus error browser after refresh");
			DW();
		}

    }

	class ErrorKeyEventListener implements CustomKeysListView.OnKeyEventListener {
		final ErrorBrowser errorBrowser;
		KeyStrokeDetector keyStrokeDetector;
		ErrorKeyEventListener(ErrorBrowser errorBrowser) {
			this.errorBrowser = errorBrowser;
			this.keyStrokeDetector = ServiceContainer.getMainActivity().getKeyStrokeDetector();
		}

		public boolean onKeyDown(int i, KeyEvent keyEvent) {
			return this.keyStrokeDetector.onKeyDown(i, keyEvent, ErrorBrowser.getKeyStrokeHandler(this.errorBrowser));
		}

		public boolean onKeyUp(int i, KeyEvent keyEvent) {
			return this.keyStrokeDetector.onKeyUp(i, keyEvent, ErrorBrowser.getKeyStrokeHandler(this.errorBrowser));
		}
	}

	class ErrorEntryItemLongClickListener implements AdapterView.OnItemLongClickListener {

		@hy
		final ErrorBrowser errorBrowser;

		final CustomKeysListView customKeysListView;

		final ClipboardManager clipboardManager;
		ErrorEntryItemLongClickListener(ErrorBrowser errorBrowser, CustomKeysListView customKeysListView) {
			this.errorBrowser = errorBrowser;
			this.customKeysListView = customKeysListView;
			Context context = errorBrowser.getContext();
			this.clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		}

		private void copyText(String msg) {
			final android.content.ClipData clipData = android.content.ClipData
				.newPlainText("", msg);
			clipboardManager.setPrimaryClip(clipData);
		}

		@Override
		public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
			ErrorEntry errorEntry = (ErrorEntry) this.customKeysListView.getItemAtPosition(position);

			if (errorEntry == null) {
				return true;				
			}
			// 
			copyText(errorEntry.describe);
			Context context = errorBrowser.getContext();
			Toast.makeText(context, "信息已复制到剪切板", Toast.LENGTH_SHORT).show();
			if (errorEntry.isFile || errorEntry.isFolder) {
				return true;
			}

			MainActivity mainActivity = ErrorBrowser.getMainActivity(this.errorBrowser);
			mainActivity.aq(errorEntry.fileSpan);
			// 相当于长按编辑器 进入编辑器菜单
			mainActivity.getAIDEEditorPager().q7();
			return true;
		}
	}

	class c implements AdapterView.OnItemClickListener {

		final CustomKeysListView customKeysListView;

		@hy
		final ErrorBrowser errorBrowser;

		c(ErrorBrowser errorBrowser, CustomKeysListView customKeysListView) {
			this.errorBrowser = errorBrowser;
			this.customKeysListView = customKeysListView;
		}

		@Override
		public void onItemClick(AdapterView<?> adapterView, View view, int i, long j) {
			ErrorEntry eVar = (ErrorEntry) this.customKeysListView.getItemAtPosition(i);
			if (eVar == null) {
				return;
			}
			MainActivity mainActivity = ErrorBrowser.getMainActivity(this.errorBrowser);
			mainActivity.aq(eVar.fileSpan);
		}
	}

	class ErrorEntry {

		public String fileName;

		public String describe;

		@hy
		final ErrorBrowser errorBrowser;

		public FileSpan fileSpan;

		public final boolean isFolder;
		public final boolean isFile;

		public boolean isWarning;

		public SyntaxError syntaxError;

		public ErrorEntry(ErrorBrowser errorBrowser, String filePath, SyntaxError syntaxError) {
			this.errorBrowser = errorBrowser;
			this.syntaxError = syntaxError;
			this.describe = syntaxError.j6();
			// 警告⚠️
			this.isWarning = syntaxError.WB != 112 && syntaxError.Zo();
			this.fileSpan = new FileSpan(filePath, syntaxError.jw, syntaxError.fY, syntaxError.qp, syntaxError.k2);

			// 两者都为false
			this.isFolder = false;
			this.isFile = false;
		}

		public ErrorEntry(ErrorBrowser errorBrowser, String filePath) {
			this.errorBrowser = errorBrowser;
			boolean KD = FileSystem.KD(filePath);
			this.isFile = KD;
			this.isFolder = !KD;

			this.fileName = FileSystem.getName(filePath);
			this.fileSpan = new FileSpan(filePath, 1, 1, 1, 1);

		}
	}

	class ErrorAdapter extends ListAdapterBase<ErrorEntry> {

		@hy
		final ErrorBrowser errorBrowser;

		private LayoutInflater layoutInflater;


		ErrorAdapter(ErrorBrowser errorBrowser, ErrorKeyEventListener aVar) {
			this(errorBrowser);
		}
		private ErrorAdapter(ErrorBrowser errorBrowser) {
			this.errorBrowser = errorBrowser;
			layoutInflater = LayoutInflater.from(this.errorBrowser.getContext());
		}


		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			convertView = convertView == null ? 
				layoutInflater.inflate(R.layout.errorbrowser_entry, parent, false) 
				: convertView;

			ErrorEntry errorEntry = (ErrorEntry) j6(position);

			View entryFile = convertView.findViewById(R.id.errorbrowserEntryFile);
			View entryError = convertView.findViewById(R.id.errorbrowserEntryError);
			ImageView entryImage = convertView.findViewById(R.id.errorbrowserEntryImage);
			TextView entryMessage = convertView.findViewById(R.id.errorbrowserEntryMessage);

			// 不是文件(包括预处理文件)也不是目录
			if (!errorEntry.isFile && !errorEntry.isFolder) {
				// 
				entryFile.setVisibility(View.GONE);

				entryError.setVisibility(View.VISIBLE);

				// 应该 用警告⚠️才对
				if (errorEntry.isWarning) {
					entryImage.setImageResource(R.drawable.project_warnings);
				} else {
					entryImage.setImageResource(R.drawable.project_errors);						
				}

				entryMessage.setText(errorEntry.describe);

				return convertView;
			}

			entryFile.setVisibility(View.VISIBLE);
			entryError.setVisibility(View.GONE);

			ImageView entryFileImage = convertView.findViewById(R.id.errorbrowserEntryFileImage);
			if (errorEntry.isFolder) {
				entryFileImage.setImageResource(R.drawable.folder);
			} else {
				entryFileImage.setImageResource(AppFileIcons.j6(errorEntry.fileName));
			}

			TextView entryFileName = convertView.findViewById(R.id.errorbrowserEntryFileName);
			entryFileName.setText(errorEntry.fileName);
			return convertView;

		}



	}
}

