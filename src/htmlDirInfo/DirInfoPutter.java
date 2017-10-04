package htmlDirInfo;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;




public class DirInfoPutter {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String strBasePath;
		if (args.length > 0) {
			strBasePath = args[0];
		} else {
			System.err.println("Usage: ");
			System.err.println("  java -jar HtmlDirInfoPutter.jar <TargetPath>");
			return;
		}

		{
			File[] files = outputPath_.listFiles();
			for (File file : files) {
				if (file.getName().endsWith(".html")) {
					file.delete();
				}
			}
		}

		try {
			System.out.println("Processing...");
			fileCount_ = -1;
			scan2(strBasePath);
			closeHtml();

			outputFileTypeSizeInfo(new File("fileTypeSizeInfo.html"));
			outputNewFileList(new File("recentUpdatedList.html"));
			outputIndex(new File("index.html"));
		} catch(IOException e) {
			e.printStackTrace();
			return;
		}
		System.out.println("終了");
	}

	private static File outputPath_ = new File(".");
	private static int currentFileIndex_ = 1;
	private static int fileCount_ = 0;
	private static HashMap<String, DirInfo> mapDirInfo_ = new HashMap<String, DirInfo>();
	// 拡張子ごとのサイズ
	private static HashMap<String, FileTypeSizeInfo> mapFileTypeSize_ = new HashMap<String, FileTypeSizeInfo>();
	//
	private static long oldestUdate_;
	private static List<FilePathInfo> newFileList_ = new ArrayList<FilePathInfo>();
	private static final int NumNewFileList = 500;

	private static DirInfo scan2(String strPath) throws IOException {
		final ArrayList<ScanContext> contextStack = new ArrayList<>();
		Files.walkFileTree(Paths.get(strPath), new FileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir,	BasicFileAttributes attrs) throws IOException {
				ScanContext c = new ScanContext();
				contextStack.add(c);
				if (fileCount_ >= 2000) {
					fileCount_ = 0;
					currentFileIndex_++;
					c.isNewIndex = true;
				} else if (fileCount_ < 0) {
					fileCount_ = 0;
					currentFileIndex_ = 1;
					c.isNewIndex = true;
				}
				c.resultInfo = new DirInfo(dir.toString(), attrs.lastModifiedTime().toMillis());
				c.resultInfo.setFileIndex(currentFileIndex_);
				mapDirInfo_.put(toHtmlTag(c.resultInfo.getPath()), c.resultInfo);
				fileCount_++;
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				ScanContext c = contextStack.get(contextStack.size() - 1);
				fileCount_++;
				// ファイル
				FileInfo info = new FileInfo(file.getFileName().toString(),  attrs.size(), attrs.lastModifiedTime().toMillis());
				c.infoList.add(info);
				// 拡張子ごとのサイズの更新
				String strName = info.getName();
				String strFileType = "* other *";
				int pos = strName.lastIndexOf('.');
				if (pos > 0) { strFileType = strName.substring(pos + 1).toLowerCase(); }
				FileTypeSizeInfo sizeInfo = mapFileTypeSize_.get(strFileType);
				if (sizeInfo == null) { mapFileTypeSize_.put(strFileType, sizeInfo = new FileTypeSizeInfo(strFileType)); }
				sizeInfo.totalSize += info.getSize();
				sizeInfo.count++;
				// 最近更新したファイル
				if (oldestUdate_ <= info.getDate()) {
					newFileList_.add(new FilePathInfo(c.resultInfo.getFileIndex(), c.resultInfo.getPath(), info.getName(), info.getDate()));
					if (newFileList_.size() >= NumNewFileList * 2) {
						Collections.sort(newFileList_);
						newFileList_ = newFileList_.subList(0, NumNewFileList);
						oldestUdate_ = newFileList_.get(NumNewFileList - 1).getDate();
					}
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				ScanContext prevC = contextStack.get(contextStack.size() - 1);
				Info[] lstInfo = new Info[prevC.infoList.size()];
				int i = 0;
				for (Info info : prevC.infoList) {
					lstInfo[i++] = info;
				}
				prevC.resultInfo.setInfoList(lstInfo);

				if (prevC.isNewIndex) {
					System.out.println("Output... index:" + prevC.resultInfo.getFileIndex() + " path=" + prevC.resultInfo.getPath());
				}

				if (prevC.isNewIndex || prevC.resultInfo.getFileIndex() != 1) {
					outputToHtml(prevC.resultInfo);
					prevC.resultInfo.markOutput();
				}
				if (contextStack.size() >= 2) {
					contextStack.remove(contextStack.size() - 1);
					ScanContext c = contextStack.get(contextStack.size() - 1);
					c.infoList.add(prevC.resultInfo);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return contextStack.get(0).resultInfo;
	}
	private static class ScanContext {
		public boolean isNewIndex = false;
		public DirInfo resultInfo;
		public ArrayList<Info> infoList = new ArrayList<>();
	}

	private static DirInfo scan(String strPath) throws IOException {
		boolean isNewIndex = false;
		if (fileCount_ >= 2000) {
			fileCount_ = 0;
			currentFileIndex_++;
			isNewIndex = true;
		} else if (fileCount_ < 0) {
			fileCount_ = 0;
			currentFileIndex_ = 1;
			isNewIndex = true;
		}

		File currentFile = new File(strPath);
		DirInfo resultInfo = new DirInfo(strPath, currentFile.lastModified());
		resultInfo.setFileIndex(currentFileIndex_);
		mapDirInfo_.put(toHtmlTag(resultInfo.getPath()), resultInfo);

		ArrayList<Info> infoList = new ArrayList<Info>();
		File[] files = currentFile.listFiles();
		if (files == null) {
			System.err.println("Could not get file list at '" + strPath + "'");
		}

		fileCount_++;

		if (files != null) {
			fileCount_ += files.length;
			for (File file : files) {
				if (Files.isSymbolicLink(file.toPath())) { continue; }
				if (file.isDirectory()) {
					// ディレクトリ
					String strNextPath = file.getAbsolutePath();
					if (!strNextPath.equals(".") && !strNextPath.equals("..")) {
						infoList.add(scan(file.getAbsolutePath()));
					}
				} else {
					// ファイル
					FileInfo info = new FileInfo(file);
					infoList.add(info);
					// 拡張子ごとのサイズの更新
					String strName = info.getName();
					String strFileType = "* other *";
					int pos = strName.lastIndexOf('.');
					if (pos > 0) { strFileType = strName.substring(pos + 1).toLowerCase(); }
					FileTypeSizeInfo sizeInfo = mapFileTypeSize_.get(strFileType);
					if (sizeInfo == null) { mapFileTypeSize_.put(strFileType, sizeInfo = new FileTypeSizeInfo(strFileType)); }
					sizeInfo.totalSize += info.getSize();
					sizeInfo.count++;
					// 最近更新したファイル
					if (oldestUdate_ <= info.getDate()) {
						newFileList_.add(new FilePathInfo(resultInfo.getFileIndex(), resultInfo.getPath(), info.getName(), info.getDate()));
						if (newFileList_.size() >= NumNewFileList * 2) {
							Collections.sort(newFileList_);
							newFileList_ = newFileList_.subList(0, NumNewFileList);
							oldestUdate_ = newFileList_.get(NumNewFileList - 1).getDate();
						}
					}
				}
			}
		}
		//Collections.sort(lstDirInfo);
		//Collections.sort(lstFileInfo);


		Info[] lstInfo = new Info[infoList.size()];
		int i = 0;
		for (Info info : infoList) {
			lstInfo[i++] = info;
		}
		resultInfo.setInfoList(lstInfo);

		if (isNewIndex) {
			System.out.println("Output... index:" + resultInfo.getFileIndex() + " path=" + resultInfo.getPath());
		}

		if (isNewIndex || resultInfo.getFileIndex() != 1) {
			outputToHtml(resultInfo);
			resultInfo.markOutput();
		}

		return resultInfo;
	}


	private static void outputToHtml(DirInfo dirInfo) throws IOException {
		writeHtml(dirInfo);
		dirInfo.markOutput();
	}


	private static String getHtmlFileName(int fileIndex) {
		return "" + fileIndex + ".html";
	}
	private static String toHtmlTag(String strText) {
		int len = strText.length();
		if (len == 0) { return ""; }
		if (strText.charAt(len - 1) == File.separatorChar) {
			strText = strText.substring(0, len - 1);
		}
		strText = strText.replace('\\', '@');
		strText = strText.replace('/', '@');
		strText = strText.replace(' ', '_');
		return strText;
	}

	private static Writer writer_;
	private static int currentWritingIndex_;
	private static Writer openHtml(int fileIndex) throws IOException {
		Writer writer;
		if (writer_ != null) {
			if (currentWritingIndex_ == fileIndex) { return writer_; }
			writer_.close();
			writer_ = null;
		}

		File file = new File(outputPath_, getHtmlFileName(fileIndex));
		if (file.exists()) {
			//System.err.println("■ APPEND!!");
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
		} else {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
			writeHeader(writer);
		}

		currentWritingIndex_ = fileIndex;
		writer_ = writer;

		return writer;
	}

	private static void writeHeader(Writer writer) throws IOException {
		writer.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n");
		writer.write("<html><head>\n");
		writer.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n");
		writer.write("<style>\n");
		writer.write("<!--\n");
		writer.write("  H2\n");
		writer.write("  {\n");
		writer.write("    BACKGROUND-COLOR: lightblue;\n");
		writer.write("    font-size: 100%;\n");
		writer.write("    font-weight: normal;\n");
		writer.write("    font-family: monospace;\n");
		writer.write("  }\n");
		writer.write("-->\n");
		writer.write("</style>\n");
		writer.write("</head>\n");
	}

	private static void closeHtml() throws IOException {
		if (writer_ != null) {
			writer_.close();
			writer_ = null;
		}

		File[] files = outputPath_.listFiles();
		for(File file : files) {
			if (file.getName().endsWith(".html")) {
				Writer writer = new FileWriter(file, true);
				writer.write("<br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br>\n");
				writer.write("<br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br>\n");
				writer.write("<br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br> <br>\n");
				writer.write("</html>\n");
				writer.close();
			}
		}
	}

	private static DecimalFormat fmtSize_ = new DecimalFormat("###,###");
	private static SimpleDateFormat fmtDate_ = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	private static void writeHtml(DirInfo dirInfo) throws IOException {
		if (dirInfo.getNonOutputCount() == 0) { return; }

		Writer writer = openHtml(dirInfo.getFileIndex());

		writer.write("<a name=\"" + toHtmlTag(dirInfo.getPath()) + "\">\n");
		writer.write("<h2>" + makePathCaption(dirInfo.getPath()) + "</h2>\n");

		writer.write("<table border=1 width=95%>\n");
		writer.write(" <tr bgcolor=#c0c0c0><td>名前<td width=128px>サイズ</td><td width=168px>更新日時</td></tr>\n");

		Info[] lstInfo = dirInfo.getInfoList().clone();
		Arrays.sort(lstInfo);
		for (Info info : lstInfo) {
			if (info instanceof DirInfo) {
				DirInfo dinfo = (DirInfo) info;
				writer.write(
						" <tr>" +
						"<td><a href=\"" +
						     getHtmlFileName(dinfo.getFileIndex()) +
						     "#" + toHtmlTag(dinfo.getPath()) + "\">" + dinfo.getName() + "</a></td>" +
						"<td align=right>" + fmtSize_.format(dinfo.getSize()) + "</td>" +
						"<td>" + fmtDate_.format(new Date(info.getDate())) + "</td></tr>\n"
						);
			} else {
				writer.write(
						" <tr>" +
						"<td>" + info.getName() + "</td>" +
						"<td align=right>" + fmtSize_.format(info.getSize()) + "</td>" +
						"<td>" + fmtDate_.format(new Date(info.getDate())) + "</td></tr>\n"
						);
			}
		}

		writer.write("</table>\n");
		writer.write("</a>\n");

		for (Info info : dirInfo.getInfoList()) {
			if (info instanceof DirInfo) {
				writeHtml((DirInfo) info);
			}
		}
	}

	private static String makePathCaption(String strPath) {
		StringBuffer sbHtml = new StringBuffer();
		StringBuffer sbDir = new StringBuffer();
		String[] strDirItems = strPath.split("\\" + File.separatorChar);
		for (int i = 0; i < strDirItems.length; i++) {
			if (i > 0) {
				sbDir.append(File.separatorChar);
				sbHtml.append(File.separatorChar);
			}
			sbDir.append(strDirItems[i]);

			if (i < strDirItems.length - 1) {
				DirInfo dirInfo = mapDirInfo_.get(toHtmlTag(sbDir.toString()));
				if (dirInfo != null) {
					sbHtml.append("<a href=\"");
					sbHtml.append(getHtmlFileName(dirInfo.getFileIndex()));
					sbHtml.append('#').append(toHtmlTag(sbDir.toString()));
					sbHtml.append("\">");
					sbHtml.append(strDirItems[i]);
					sbHtml.append("</a>");
				} else {
					sbHtml.append(strDirItems[i]);
				}
			} else {
				sbHtml.append(strDirItems[i]);
			}
		}
		if (strDirItems.length == 1) {
			sbHtml.append(File.separatorChar);
		}

		return sbHtml.toString();
	}

	/**
	 * 拡張子ごとの使用容量を出力します。
	 * @param file 出力先ファイル
	 * @throws IOException
	 */
	private static void outputFileTypeSizeInfo(File file) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
		writeHeader(writer);

		writer.write("<h2>拡張子ごとの使用容量</h2>\n");
		writer.write("<table border=1>\n");
		writer.write(" <tr bgcolor=#c0c0c0><td>拡張子</td><td>ファイル数</td><td>サイズ</td></tr>\n");
		ArrayList<FileTypeSizeInfo> countList = new ArrayList<FileTypeSizeInfo>(mapFileTypeSize_.values());
		Collections.sort(countList);
		for (FileTypeSizeInfo c : countList) {
			writer.write(" <tr><td>" + c.name + "</td><td align=right>" + fmtSize_.format(c.count) + "</td><td align=right>" + fmtSize_.format(c.totalSize) + "</td></tr>\n");
		}
		writer.write("</table>\n");

		writer.write("</html>\n");
		writer.close();
	}

	/**
	 * 最近更新されたファイル一覧を出力します。
	 * @param file 出力先ファイル
	 * @throws IOException
	 */
	private static void outputNewFileList(File file) throws IOException {
		Collections.sort(newFileList_);
		newFileList_ = newFileList_.subList(0, Math.min(NumNewFileList, newFileList_.size()));

		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
		writeHeader(writer);

		writer.write("<h2>最近更新されたファイル一覧</h2>\n");
		writer.write("<table border=1>\n");
		writer.write(" <tr bgcolor=#c0c0c0><td>ファイル</td><td width=168px>更新日時</td></tr>\n");
		for (int i = 0; i < NumNewFileList && i < newFileList_.size(); i++) {
			FilePathInfo info = newFileList_.get(i);
			writer.write(" <tr><td>" +
					"<a href=\"" + getHtmlFileName(info.getBasePathIndex()) +
					"#" + toHtmlTag(info.getBasePath()) + "\"" +
					" title=\"" + info.getBasePath() + "\"" +
					">" +
					info.getFileName() + "</a>" +
					"</td><td>" + fmtDate_.format(new Date(info.getDate())) + "</td></tr>\n");
		}


		writer.write("</table>\n");

		writer.write("</html>\n");
		writer.close();
	}

	private static void outputIndex(File file) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
		writeHeader(writer);

		writer.write("<h2>INDEX</h2>\n");
		writer.write("<ul>\n");
		writer.write(" <li><a href='1.html'>ディレクトリの使用状況</a>\n");
		writer.write(" <li><a href='recentUpdatedList.html'>最近更新されたファイル</a>\n");
		writer.write(" <li><a href='fileTypeSizeInfo.html'>拡張子ごとの使用容量</a>\n");
		writer.write("</ul>\n");

		writer.write("</html>\n");
		writer.close();
	}

	// ===========================================================================================================
	// 内部クラス
	// ===========================================================================================================

	private static abstract class Info implements Comparable<Info> {
		public abstract long getSize();
		public abstract String getName();
		public abstract long getDate();

		public int compareTo(Info info) {
			long sgn = info.getSize() - getSize();
			if (sgn > 0) { return 1; }
			if (sgn < 0) { return -1; }
			return 0;
		}

		public abstract int getNonOutputCount();
	}

	private static class FileInfo extends Info {
		private long size_;
		private String strFileName_;
		private long udate_;

		public FileInfo(File file) {
			strFileName_ = file.getName().intern();
			size_ = file.length();
			udate_ = file.lastModified();
		}
		public FileInfo(String name, long size, long lastModified) {
			strFileName_ = name;
			size_ = size;
			udate_ = lastModified;
		}

		public int compareTo(Info info) {
			if (info instanceof DirInfo) { return 1; }
			return super.compareTo(info);
		}

		public long getSize() { return size_; }
		public String getName() { return strFileName_; }
		public long getDate() { return udate_; }
		public int getNonOutputCount() { return 0; }
	}

	private static class DirInfo extends Info {
		private String strPath_;
		private Info[] lstInfo_;
		private long size_;
		private long udate_;
		private int fileIndex_;
		private int nonOutputCount_;

		public DirInfo(String strPath, long date) {
			strPath_ = strPath;
			udate_ = date;

			size_ = 0;
		}

		public int compareTo(Info info) {
			if (info instanceof FileInfo) { return -1; }
			return super.compareTo(info);
		}

		public long getSize() { return size_; }
		public long getDate() { return udate_; }
		public String getName() {
			int pos = strPath_.lastIndexOf(File.separator);
			if ( pos < 0 ) { return strPath_; }

			return strPath_.substring(pos + 1);
		}

		public String getPath() { return strPath_; }
		public Info[] getInfoList() { return lstInfo_; }
		public void setInfoList(Info[] lstInfo) {
			lstInfo_ = lstInfo;

			nonOutputCount_ = 1;
			for (int i = 0; i < lstInfo.length; i++) {
				Info info = lstInfo[i];
				if (udate_ < info.getDate()) { udate_ = info.getDate(); }
				size_ += info.getSize();
				nonOutputCount_ += lstInfo[i].getNonOutputCount() + 1;
			}
		}

		public int getFileIndex() { return fileIndex_; }
		public void setFileIndex(int fileIndex) { fileIndex_ = fileIndex; }

		public int getNonOutputCount() { return nonOutputCount_; }

		public void markOutput() {
			nonOutputCount_ = 0;
			mapDirInfo_.remove(toHtmlTag(strPath_));
			if (lstInfo_ != null) {
				for (Info info : lstInfo_) {
					if (info instanceof DirInfo) { ((DirInfo)info).markOutput(); }
				}
				lstInfo_ = null;
			}
		}
	}

	private static class FileTypeSizeInfo implements Comparable<FileTypeSizeInfo>{
		public long totalSize;
		public int  count;
		public String name;

		public FileTypeSizeInfo(String name) { this.name = name; }

		public int compareTo(FileTypeSizeInfo c) {
			long sgn = c.totalSize - totalSize;
			if (sgn > 0) { return 1; }
			if (sgn < 0) { return -1; }
			return 0;
		}
	}

	private static class FilePathInfo implements Comparable<FilePathInfo> {
		private String basePath_;
		private String fileName_;
		private int basePathIndex_;
		private long udate_;

		public FilePathInfo(int basePathIndex, String basePath, String fileName, long udate) {
			basePathIndex_ = basePathIndex;
			basePath_ = basePath;
			fileName_ = fileName;
			udate_ = udate;
		}

		public int compareTo(FilePathInfo info) {
			long sgn = info.udate_ - udate_;
			if (sgn > 0) { return 1; }
			if (sgn < 0) { return -1; }
			return 0;
		}

		public long getDate() { return udate_; }
		public String getBasePath() { return basePath_; }
		public String getFileName() { return fileName_; }
		public int getBasePathIndex() { return basePathIndex_; }
	}
}
