package com.mavennet.iqdatahubtest;

import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@SpringBootApplication
@RestController
public class Application {

	private final String fileURL = "https://docs.google.com/spreadsheets/d/1LMrx_9P5umJTk7YEFAHnJ0UB1Bv-oYtE-Q8tJLIVySQ/export?format=xlsx";
	//private String fileURL = "https://docs.google.com/spreadsheets/d/15VYDPk4GAknpYh65476yfQjhTn8qsCt05arZXL7aM58/export?format=xlsx";

	private final List<String> compareColumns = Arrays.asList(
			"Fund Price",
			"Previous Price",
			"Price Change",
			"Total Shares Outstanding",
			"Total Net Assets",
			"Mngt Fee",
			"Ops Exp",
			"HST",
			"Waiver",
			"OPENING EQUITY",
			"Contributions",
			"Redemptions",
			"Distributions",
			"Reinvested",
			"Dilutions [Equity]	",
			"0 Issuance Cost = - Issuance Cost",
			"Adjustment",
			"Adjusted Opening Equity",
			"Net Income Before Fees",
			"Management Fees",
			"Distributions",
			"NAV",
			"OPENING UNITS",
			"Unit Contributions",
			"Unit Redemptions",
			"Unit Dist. Reinvested",
			"Unit Adj",
			"Ending Units",
			"NAVPU",
			"Previous NAVPU",
			"Valuation Return",
			"Payments Mngt Fee",
			"Payments Ops Exp",
			"Payments HST",
			"Payments Waiver",
			"Subscription Units",
			"Subscription Value",
			"Redemptions Units",
			"Redemptions Value",
			"navpu_USD",
			"navpu_CAD"/*,
			"1MO_USD",
			"3MO_USD",
			"6MO_USD",
			"1YR_USD",
			"3YR_USD",
			"ITD_USD",
			"YTD_USD",
			"MTD_CAD",
			"3MO_CAD",
			"6MO_CAD",
			"1YR_CAD",
			"3YR_CAD",
			"ITD_CAD",
			"YTD_CAD"*/
			);

	DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	public Map<String, Map<Integer, List<String>>> readData(File f) throws IOException{
		FileInputStream file = new FileInputStream(f);
		Workbook workbook = new XSSFWorkbook(file);

		Map<String, Map<Integer, List<String>>> data = new HashMap<>();

		for(int k = 0; k < workbook.getNumberOfSheets(); k++){
			Sheet sheet = workbook.getSheetAt(k);

			String sheetName =  sheet.getSheetName().replaceAll("[^\\x00-\\x7F]", "").trim();
			Map<Integer, List<String>> sheetData = new HashMap<>();
			List<String> headers = new ArrayList<>();
			int i = 0;
			for (Row row : sheet) {
				List<String> rowData = new ArrayList<>();
				if(i == 0){
					for (Cell cell : row) {
						switch (cell.getCellType()) {
							case STRING -> {
								String cellValue = cell.getStringCellValue();
								if(cellValue.equals("Waiver/Blended HST")){
									rowData.add("Waiver");
								}else{
									rowData.add(cellValue);
								}
							}
							case NUMERIC -> {
								if (DateUtil.isCellDateFormatted(cell)) {
									rowData.add(dateFormat.format(cell.getDateCellValue()));
								} else {
									rowData.add(String.format("%.2f", cell.getNumericCellValue()));
								}
							}
							default -> rowData.add("");
						}
					}
					headers.addAll(rowData);
				}else{
					// Need to treat blank values in a special way as per https://poi.apache.org/components/spreadsheet/quick-guide.html#Iterator
					for(int colIndex=0; colIndex < headers.size(); colIndex++){
						Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
						if (cell == null) {
							rowData.add("");
						} else {
							switch (cell.getCellType()) {
								case STRING -> rowData.add(cell.getStringCellValue());
								case NUMERIC -> {
									if (DateUtil.isCellDateFormatted(cell)) {
										rowData.add(dateFormat.format(cell.getDateCellValue()));
									} else {
										rowData.add(String.format("%.2f", cell.getNumericCellValue()));
									}
								}
								default -> rowData.add("");
							}
						}
					}
				}

				sheetData.put(i++, rowData);
			}

			data.put(sheetName, sheetData);
		}

		return data;
	}

	@GetMapping(value= {"/compareData/{dte}", "/compareData"})
	public Map<String, ComparisonResult> compareData(@PathVariable(required = false) LocalDate dte) throws Exception {

		Map<String, ComparisonResult> result = new HashMap<>();

		File file = File.createTempFile("Data", ".xlsx");
		System.out.println("Getting the file..");
		FileUtils.copyURLToFile(new URL(fileURL), file);
		System.out.println("Reading the data from the file..");
		Map<String, Map<Integer, List<String>>> data = readData(file);
		System.out.println("Comparing the data..");

		for(String sheetName: data.keySet()){
			List<String> sheetResult = new ArrayList<>();
			Map<Integer, List<String>> sheetData = data.get(sheetName);
			Map<String, Map<Integer, Integer>> columnsToCompare = new HashMap<>();
			int comparedItems = 0;

			for(Integer key: sheetData.keySet()){
				List<String> lineData = sheetData.get(key);
				if(key == 0){
					// Line with column headers
					// Need to find out which columns are eligible for comparison
					// If column is eligible for comparison, remember the index of the columns to be compared
					for (int i = 0; i < lineData.size(); i++) {
						String column = lineData.get(i);
						if(column.endsWith(" (USD)")){
							column = column.substring(0, column.lastIndexOf(" (USD)"));
						}else if(column.endsWith(" (CAD)")){
							column = column.substring(0, column.lastIndexOf(" (CAD)"));
						}
						// Only compare the columns that are eligible for comparison
						if(compareColumns.contains(column)){
							if(columnsToCompare.containsKey(column)){
								Map<Integer, Integer> comparedIndexes = columnsToCompare.get(column);
								Integer indexKey = (Integer) comparedIndexes.keySet().toArray()[0];
								if(comparedIndexes.get(indexKey) == null){
									columnsToCompare.replace(column, Map.of(indexKey, i));
								}
							}else{
								Map<Integer, Integer> colIndexes = new HashMap<>();
								colIndexes.put(i , null);
								columnsToCompare.put(column, colIndexes);
							}
						}
					}
				}else{
					for(String columnName: columnsToCompare.keySet()){
						Map<Integer, Integer> colIndexes = columnsToCompare.get(columnName);
						Integer scriptColIndex = (Integer) colIndexes.keySet().toArray()[0];
						Integer manualColIndex = colIndexes.get(scriptColIndex);
						if(scriptColIndex != null && manualColIndex != null && lineData.size() != 0 && lineData.size() > manualColIndex){
							String scriptStr = lineData.get(scriptColIndex);
							String manualStr = lineData.get(manualColIndex);
							float scriptData;
							try {
								scriptData = scriptStr.length() > 0 ? Float.parseFloat(scriptStr.replace(",", "")) : 0;
							}catch (NumberFormatException e){
								scriptData = 0;
							}
							float manualData;
							try{
								manualData = manualStr.length()>0?Float.parseFloat(manualStr.replace(",","")):0;
							}catch (NumberFormatException e){
								manualData = 0;
							}

							int valDateColumn;
							if(sheetName.equals(" NAV BTCQ USD comparision")){
								valDateColumn = 1;
							}else{
								valDateColumn = 0;
							}

							String valDateStr = lineData.get(valDateColumn);
							try{
								if(valDateStr != null && valDateStr.trim().length() > 0){
									LocalDate valDate = LocalDate.parse(lineData.get(valDateColumn));
									if(dte == null || valDate.isEqual(dte) || valDate.isAfter(dte)){
										comparedItems = comparedItems + 1;
										if(scriptData != manualData && manualData != 0){
											sheetResult.add(String.format("%s | %s | %s | %s <> %s", sheetName, columnName, valDate, scriptData, manualData));
										}
									}
								}
							}catch(DateTimeParseException e){
								System.err.printf("Error parsing date %s\n", valDateStr);
							}
						}
					}
				}
			}

			if(comparedItems != 0){
				result.put(sheetName, new ComparisonResult(String.format ("%.2f%%", (1 - ((float) sheetResult.size() / (float) comparedItems))*100), sheetResult));
				System.out.printf("Compared sheet %s , %d mistmatches found%n\n", sheetName, sheetResult.size());
			}
		}

		System.out.println("Done.");

		return result;
	}
}
