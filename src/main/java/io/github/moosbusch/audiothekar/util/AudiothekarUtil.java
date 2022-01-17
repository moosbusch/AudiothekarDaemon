/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.moosbusch.audiothekar.util;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author komano
 */
public class AudiothekarUtil {

    private static final Logger LOGGER = LogManager.getLogger(AudiothekarUtil.class);

    public static String removeBOM(final String inputStr) {
        String result = inputStr;
        
        result = StringUtils.removeStart(result, ByteOrderMark.UTF_16BE.toString());
        result = StringUtils.removeStart(result, ByteOrderMark.UTF_16LE.toString());
        result = StringUtils.removeStart(result, ByteOrderMark.UTF_32BE.toString());
        result = StringUtils.removeStart(result, ByteOrderMark.UTF_32LE.toString());
        result = StringUtils.removeStart(result, ByteOrderMark.UTF_8.toString());
        
        return result;
    }
    
    public static String encodeFileObjectNameUTF8(final String dirOrFilename, final int maxInputLength) {
        return URLEncoder.encode(StringUtils.abbreviate(dirOrFilename, maxInputLength), StandardCharsets.UTF_8);
    }

    public static String decodeFileObjectNameUTF8(final String dirOrFilename) {
        return URLDecoder.decode(dirOrFilename, StandardCharsets.UTF_8);
    }

    public static void convertTree(final File startDir) throws IOException {
        Files.walkFileTree(startDir.toPath(), new RenamingFileVisitorImpl());
    }
/*
    public static void listTree(final File startDir) throws IOException {
        final ListingContentFileVisitorImpl fileVisitor = new ListingContentFileVisitorImpl();

        Files.walkFileTree(startDir.toPath(), fileVisitor);

        fileVisitor.saveWorkbook(new File("/data/data/podcasts/content.xslx"));
    }

    private static class ListingContentFileVisitorImpl extends SimpleFileVisitor {

        private final XSSFWorkbook workbook;
        private int rowCnt = 0;

        public ListingContentFileVisitorImpl() {
            this.workbook = initWorkbook();
        }

        private XSSFWorkbook initWorkbook() {
            final XSSFWorkbook result = new XSSFWorkbook(XSSFWorkbookType.XLSX);
            final XSSFSheet sheet = result.createSheet();

            return result;
        }

        private XSSFSheet getSheet() {
            return getWorkbook().getSheetAt(0);
        }

        private XSSFWorkbook getWorkbook() {
            return workbook;
        }

        @Override
        public FileVisitResult visitFile(final Object file, final BasicFileAttributes attrs) throws IOException {
            final Path currentPath;

            if (attrs.isRegularFile()) {
                if (file instanceof Path) {
                    final XSSFSheet sheet = getSheet();
                    final XSSFRow currentRow;
                    final XSSFCell pathCell;
                    final XSSFCell filenameCell;
                    final XSSFCell decodedFilenameCell;
                    final XSSFCell v1TitleCell;
                    final XSSFCell v2TitleCell;
                    final String fileName;
                    final String decodedFileName;
                    final String pathOfFile;
                    Mp3File mp3File = null;

                    currentPath = (Path) file;
                    fileName = currentPath.getFileName().toString();
                    decodedFileName = decodeFileObjectNameUTF8(fileName);
                    pathOfFile = currentPath.getParent().toString();

                    if (currentPath.toString().toLowerCase().endsWith("mp3")) {
                        final String v1Title;
                        final String v2Title;
                        final ID3v1 id3v1Tag;
                        final ID3v2 id3v2Tag;
                        
                        try {
                            mp3File = new Mp3File(currentPath.toFile());
                        } catch (UnsupportedTagException | InvalidDataException ex) {
                            LOGGER.error(ex.getMessage());
                        }

                        if (mp3File != null) {
                            LOGGER.info("Visiting file: " + currentPath.toString());
                            LOGGER.info("Path of file: " + pathOfFile);

                            currentRow = sheet.createRow(rowCnt);
                            pathCell = currentRow.createCell(0);
                            filenameCell = currentRow.createCell(1);
                            decodedFilenameCell = currentRow.createCell(2);
                            v1TitleCell = currentRow.createCell(3);
                            v2TitleCell = currentRow.createCell(4);
                            id3v1Tag = mp3File.getId3v1Tag();
                            id3v2Tag = mp3File.getId3v2Tag();
                            
                            if (id3v1Tag != null) {
                                v1Title = id3v1Tag.getTitle();
                            } else {
                                v1Title = "";
                            }
                            
                            if (id3v2Tag != null) {
                                v2Title = id3v2Tag.getTitle();
                            } else {
                                v2Title = "";
                            }
                            
                            pathCell.setCellType(CellType.STRING);
                            filenameCell.setCellType(CellType.STRING);
                            decodedFilenameCell.setCellType(CellType.STRING);
                            v1TitleCell.setCellType(CellType.STRING);
                            v2TitleCell.setCellType(CellType.STRING);

                            pathCell.setCellValue(pathOfFile);
                            filenameCell.setCellValue(fileName);
                            decodedFilenameCell.setCellValue(decodedFileName);
                            v1TitleCell.setCellValue(v1Title);
                            v2TitleCell.setCellValue(v2Title);
                        }
                    }

                    rowCnt++;
                }
            }

            return super.visitFile(file, attrs);
        }

        public void saveWorkbook(final File outFile) throws FileNotFoundException, IOException {
            final BufferedOutputStream bufOut;

            if (!outFile.exists()) {
                outFile.createNewFile();
            }

            getSheet().autoSizeColumn(0);
            getSheet().autoSizeColumn(1);
            getSheet().autoSizeColumn(2);
            getSheet().autoSizeColumn(3);
            getSheet().autoSizeColumn(4);

            bufOut = new BufferedOutputStream(new FileOutputStream(outFile));

            getWorkbook().write(bufOut);
            bufOut.flush();
            bufOut.close();
        }

    }
*/
    private static class RenamingFileVisitorImpl extends SimpleFileVisitor {

        
        private static final Pattern startWithDatePattern = Pattern.compile("^[0-9]{4}\\-[0-9]{2}\\-[0-9]{2}\\-.*$");
        
        @Override
        public FileVisitResult visitFileFailed(Object file, IOException exc) throws IOException {
            LOGGER.error(exc.getMessage());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Object file, final BasicFileAttributes attrs) throws IOException {
            final Path currentPath;

            if (attrs.isRegularFile()) {
                if (file instanceof Path) {
                    final Matcher startWithDatePatternMatcher;
                    final String oldFileName;
                    final String decodedOldFileName;
                    final String newFileNameofDecoded;
                    final String pathOfFile;
                    Path newFile;
                    String newFileName;

                    currentPath = (Path) file;
                    oldFileName = currentPath.getFileName().toString();
                    decodedOldFileName = decodeFileObjectNameUTF8(oldFileName);
                    newFileName = encodeFileObjectNameUTF8(oldFileName, 128);
                    newFileNameofDecoded = encodeFileObjectNameUTF8(decodedOldFileName, 128);
                    pathOfFile = currentPath.getParent().toString();
                    startWithDatePatternMatcher = startWithDatePattern.matcher(oldFileName);

                    LOGGER.info("Visiting file: " + currentPath.toString());
                    LOGGER.info("Path of file: " + pathOfFile);

                    if ((!StringUtils.equals(oldFileName, newFileName)) && (!StringUtils.equals(oldFileName, newFileNameofDecoded))) {
                        LOGGER.info("Renaming file: " + oldFileName + " to " + newFileName);
                        newFile = Path.of(pathOfFile, newFileName);
                        Files.move(currentPath, newFile, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        LOGGER.info("Not renaming file: " + oldFileName);
                    }
                    
                    if (startWithDatePatternMatcher.matches()) {
                        newFileName = oldFileName.substring(11);
                        LOGGER.info("Renaming file: " + oldFileName + " to " + newFileName);
                        newFile = Path.of(pathOfFile, newFileName);
                        Files.move(currentPath, newFile, StandardCopyOption.REPLACE_EXISTING);
                    }

                }
            }

            return super.visitFile(file, attrs);
        }

        @Override
        public FileVisitResult postVisitDirectory(Object dir, IOException exc) throws IOException {
            final Path currentPath;

            if (dir instanceof Path) {
                currentPath = (Path) dir;

                final String oldDirName = currentPath.getFileName().toString();
                final String decodedOldDirName = decodeFileObjectNameUTF8(oldDirName);
                final String newDirName = encodeFileObjectNameUTF8(oldDirName, 128);
                final String newDirNameofDecoded = encodeFileObjectNameUTF8(decodedOldDirName, 128);
                final String pathOfDir = currentPath.getParent().toString();
                final Path newDir;

                if ((!StringUtils.equals(oldDirName, newDirName)) && (!StringUtils.equals(oldDirName, newDirNameofDecoded))) {
                    LOGGER.info("Renaming directory: " + oldDirName + " to " + newDirName);
                    newDir = Path.of(pathOfDir, newDirName);
                    Files.move(currentPath, newDir, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    LOGGER.info("Not renaming directory: " + oldDirName);
                }

            }

            return super.postVisitDirectory(dir, exc);
        }

    }

}
