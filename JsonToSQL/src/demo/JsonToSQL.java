package demo;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonToSQL {

	public static void main(String[] args) {
		String jsonString = """
{
  "and": [
    {
      "startsWith": [
        {
          "var": "firstName"
        },
        "Stev"
      ]
    },
    {
      "in": [
        {
          "var": "lastName"
        },
        [
          "Vai",
          "Vaughan"
        ]
      ]
    },
    {
      ">": [
        {
          "var": "age"
        },
        "28"
      ]
    },
    {
      "or": [
        {
          "==": [
            {
              "var": "isMusician"
            },
            true
          ]
        },
        {
          "==": [
            {
              "var": "instrument"
            },
            "Guitar"
          ]
        }
      ]
    },
    {
      "==": [
        {
          "var": "groupedField1"
        },
        {
          "var": "groupedField4"
        }
      ]
    },
    {
      "<=": [
        "1954-10-03",
        {
          "var": "birthdate"
        },
        "1960-06-06"
      ]
    },
    {
      "==": [
        {
          "var": "firstName"
        },
        null
      ]
    },
    {
      "!=": [
        {
          "var": "firstName"
        },
        null
      ]
    },
    {
      "startsWith": [
        {
          "var": "firstName"
        },
        ""
      ]
    },
    {
      "==": [
        {
          "var": "firstName"
        },
        null
      ]
    },
    {
      "!=": [
        {
          "var": "firstName"
        },
        null
      ]
    },
    {
      "!": {
        "in": [
          "",
          {
            "var": "firstName"
          }
        ]
      }
    },
    {
      "startsWith": [
        {
          "var": "firstName"
        },
        ""
      ]
    }
  ]
}
		""";
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode conditions = objectMapper.readTree(jsonString);
			String sqlExpression;

			sqlExpression = convertJsonToSql(conditions);


			System.out.println(sqlExpression);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	private static String convertJsonToSql(JsonNode condition) {
		if (condition.isEmpty() || condition.isBoolean()) {
			return "()";
		}
		if (condition.isObject()) {
			String operator = condition.fieldNames().next();
			JsonNode operands = condition.get(operator);
			
			switch (operator.toLowerCase()) {
			case "!":
				return handleNotOperator(operator, operands);
			case "and":
			case "or":
				return handleLogicalOperator(operator, operands);
			case "=":
			case "!=":
			case "<":
			case ">":
			case "<=":
			case ">=":
			case "==":
				return handleComparisonOperator(operator, operands);
			case "in":
				return handleInOperator(operands);
			case "startswith":
				return handleStartsWithOperator(operator,operands);
			case "endswith":
				return handleEndsWithOperator(operator, operands);
			case "!startswith":
				return handleDoesNotBeginWithOperator(operands);
			default:
				throw new IllegalArgumentException("Unsupported operator: " + operator);
			}
		}
		return "";
	}
	

	private static String handleNotOperator(String operator, JsonNode operands) {
		Iterator<String> fieldNames = operands.fieldNames();
		String fieldName = null ;
		while (fieldNames.hasNext()) {
		    fieldName = fieldNames.next();
		}
		
		if(operator.equals("!") && fieldName.equals("startsWith")) {
				return handleDoesNotBeginWithOperator(operands);	
		}
		if(operator.equals("!") && fieldName.equals("endsWith")) {
			return handleDoesNotEndWithOperator(operands);	
		}
		if(operator.equals("!") && operands.get("in").get(1).isObject()) {
			return  handleNotContainOperator(operands);
		}
		if(operator.equals("!") && operands.get("in").get(1).isArray()) {
			return handleNotInOperatorMethod(operands);
		}
		
		StringBuilder result = new StringBuilder("(");
		for (JsonNode operand : operands) {
			result.append(convertJsonToSql(operands)).append(operator).append(" ");
		}
		result.setLength(result.length() - operator.length() - 1);
		result.append(")");
		return result.toString();
	}

	private static String handleLogicalOperator(String operator, JsonNode operands) {
		StringBuilder result = new StringBuilder("(");
		for (JsonNode operand : operands) {
			result.append(convertJsonToSql(operand)).append(" ").append(operator).append(" ");
		}
		result.setLength(result.length() - operator.length() - 2); 
		result.append(")");
		return result.toString();
	}

	private static String handleComparisonOperator(String operator, JsonNode operands) {
		if (operator.equals("==")) {
			if (operands.get(1).isNull()) {
				return operands.get(0).get("var").asText() + " is null";
			} else if (operands.get(1).isBoolean()) {
				return operands.get(0).get("var").asText() + " = " + operands.get(1);
			} else if (operands.get(1).asText().matches("M|F|O")) {
				return operands.get(0).get("var").asText() + " = '" + operands.get(1).asText() + "'";
			} else if (operands.get(1).isObject()) {
				return operands.get(0).get("var").asText() + " = " + operands.get(1).get("var").asText();
			} else {
				return operands.get(0).get("var").asText() + " " + "=" + " '" + operands.get(1).asText() + "' ";
			}
		} else if (operator.equals("<=")) {
			if (operands.get(0).isObject() == operands.get(operands.size() - 1).isObject()) {
				return operands.get(1).get("var").asText() + " BETWEEN '" + operands.get(0).asText() + "'" + " and '"
						+ operands.get(operands.size() - 1).asText() + "'";
			} else {
				return operands.get(0).get("var").asText() + " " + operator + " '" + operands.get(1).asText() + "'";
			}
		}

		else if (operator.equals("!=")) {
			if (operands.get(1).isNull()) {
				return operands.get(0).get("var").asText() + " is not null";
			} else {
				return operands.get(0).get("var").asText() + " " + operator + " '" + operands.get(1).asText() + "'";
			}
		} else {
			return operands.get(0).get("var").asText() + " " + operator + " '" + operands.get(1).asText() + "'";
		}
	}

	private static String handleInOperator(JsonNode operands) {
		if (operands.get(1).isArray()) {
			StringBuilder result = new StringBuilder(operands.get(0).get("var").asText() + " IN (");
			for (JsonNode value : operands.get(1)) {
				result.append("'").append(value.asText()).append("', ");
			}
			result.setLength(result.length() - 2);
			result.append(")");
			return result.toString();
		} else {
			StringBuilder result = new StringBuilder(operands.get(1).get("var").asText() + " Like '%");
			result.append(operands.get(0).asText()).append(", ");
			result.setLength(result.length() - 2);
			result.append("%'");
			return result.toString();
		}

	}
	private static String handleNotContainOperator(JsonNode operands) {
		StringBuilder result = new StringBuilder(operands.get("in").get(1).get("var").asText() + " Not LIKE  '%");
		for (JsonNode value : operands) {
			result.append(operands.get("in").get(0).asText());
		}
		result.setLength(result.length());
		result.append("%'");
		return result.toString();
	}

	private static String handleNotInOperatorMethod(JsonNode operands) {
			StringBuilder result = new StringBuilder(operands.get("in").get(0).get("var").asText() + " Not in (");
			for(int i = 0; i < operands.get("in").get(1).size();i++) {
				for (JsonNode value : operands) {
					result.append("'").append(operands.get("in").get(1).get(i).asText()).append("', ");
				}
			}	
			result.setLength(result.length()-2);
			result.append(")");
			return result.toString();
	}

	private static String handleStartsWithOperator(String operator, JsonNode operands) {
		return operands.get(0).get("var").asText() + " LIKE '" + operands.get(1).asText() + "%'";
	}

	private static String handleEndsWithOperator(String operator, JsonNode operands) {
		if (operator.equals("endsWith")) {
			return operands.get(0).get("var").asText() + " Not LIKE '%" + operands.get(1).asText() + "'";
		}
		return operands.get(0).get("var").asText() + " LIKE '%" + operands.get(1).asText() + "'";
	}

	public static String handleDoesNotBeginWithOperator(JsonNode operands) {
		return operands.get("startsWith").get(0).get("var").asText() + " NOT LIKE '"
				+ operands.get("startsWith").get(1).asText() + "%'";
	}

	public static String handleDoesNotEndWithOperator(JsonNode operands) {
		return operands.get("endsWith").get(0).get("var").asText() + " NOT LIKE '%"
				+ operands.get("endsWith").get(1).asText() + "'";
	}
}
