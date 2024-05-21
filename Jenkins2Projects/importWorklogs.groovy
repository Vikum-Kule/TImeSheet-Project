import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.*
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import java.security.SecureRandom;
import java.Utils.*
import groovy.json.JsonOutput;
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import jenkins.model.Jenkins
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import hudson.util.Secret

import java.net.URLEncoder;

String tempoClientId = 'TEMPO_CLIENT_ID'
String tempoClientSecret = 'TEMPO_CLIENT_SECRET'
String jiraCode = 'JIRA_CODE'
String jiraClientId = 'JIRA_CLIENT_ID'
String jiraClientSecret = 'JIRA_CLIENT_SECRET' 
String tempoRefreshBody;
String jiraRefreshBody;
tempoRefreshToken = 'TEMPO_REFRESH_TOKEN'
tempoAccessToken = null
jiraAccessToken = null
jiraRefreshToken = 'JIRA_REFRESH_TOKEN'
Boolean isDeleteProcess = params.DELETE_PROCESS

createdWorkLogs = []
retryCount  = 0 
worklogKeys = null

def sendGetRequest(url, header, platform, refreshTokenPayload) {
    // println("Request Header: ${header}")
    def response = httpRequest(url: url,
                               customHeaders: header ,
                               httpMode: 'GET',
                               validResponseCodes: '200, 204,401,403, 404')
    // Check if the request was successful or not
    if (response.status == 401 || (response.status == 404 &&  platform == "JIRA")){
      if (platform == "TEMPO"){
        println("update refresh token for tempo")
          //update header with new access token
          refreshTokens(platform, refreshTokenPayload)
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${tempoAccessToken}"
              }
          }
          sendGetRequest(url, header, platform, refreshTokenPayload)
      }else if (platform == "JIRA"){
          //update header with new access token
          refreshTokens(platform, refreshTokenPayload)
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${jiraAccessToken}"
              }
          }
          sendGetRequest(url, header, platform, refreshTokenPayload)

      }
    }
    else{
      println(response)
      return response
    }
}

def sendDeleteRequest(url, header, platform, refreshTokenPayload) {
    // println("Request Header: ${header}")
    def response = httpRequest(url: url,
                               customHeaders: header ,
                               httpMode: 'DELETE',
                               validResponseCodes: '200, 204,401,403, 404')
    // Check if the request was successful or not
    if (response.status == 401 || (response.status == 404 &&  platform == "JIRA")){
      if (platform == "TEMPO"){
        println("update refresh token for tempo")
          //update header with new access token
          refreshTokens(platform, refreshTokenPayload)
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${tempoAccessToken}"
              }
          }
          sendGetRequest(url, header, platform, refreshTokenPayload)
      }else if (platform == "JIRA"){
          //update header with new access token
          refreshTokens(platform, refreshTokenPayload)
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${jiraAccessToken}"
              }
          }
          sendGetRequest(url, header, platform, refreshTokenPayload)

      }
    }
    else{
      println(response)
      return response
    }
}


def sendPostRequest( url, payload, header, platform, refreshTokenPayload) {   
        retryCount = retryCount + 1
        def response = httpRequest acceptType: 'APPLICATION_JSON',
                    contentType: 'APPLICATION_JSON',
                    customHeaders: header,
                    httpMode: 'POST',
                    requestBody: payload,
                    url: url,
                    validResponseCodes: '200, 201, 400, 401, 403, 404'
        if (response.status == 401){
            if (platform == "TEMPO"){
            //update header with new access token
            refreshTokens(platform, refreshTokenPayload)
            header.each { data ->
                if (data.name == "Authorization") {
                    data.value = "Bearer ${tempoAccessToken}"
                }
            }
            sendPostRequest( url, payload, header, platform, refreshTokenPayload)
            }
        }else if((response.status == 403 || response.status == 400) &&  retryCount < 2){
            println("Retrying...")
            sendPostRequest( url, payload, header, platform, refreshTokenPayload)
        }
        else{
        println(response)
        return response
        }
}
def refreshTokens(platform, refreshTokenPayload){
    switch (platform) {
      case "TEMPO":
          println "Tempo Refresh Token Update"
          String tempoRefreshUrl = 'https://api.tempo.io/oauth/token/?grant_type=&client_id=&client_secret=&refresh_token'
          def tempoHeader = [[
                                  name: "Content-Typen",
                                  value: "application/x-www-form-urlencoded"
                              ]]

          def tempoTokenPayload = refreshTokensRequest(tempoRefreshUrl, refreshTokenPayload, tempoHeader)
          // save on disk
          def jsonResponse = readJSON text: tempoTokenPayload.content
          tempoAccessToken = jsonResponse.access_token
          def refreshToken = jsonResponse.refresh_token
          updateTokens(refreshToken, tempoRefreshToken)
          break
      case "JIRA":
          println "Jira Refresh Token Update"
          String tempoRefreshUrl = 'https://auth.atlassian.com/oauth/token'
          def jiraHeader = [[
                                  name: "Content-Typen",
                                  value: "application/x-www-form-urlencoded"
                              ]]
          def jiraTokenPayload = refreshTokensRequest(tempoRefreshUrl, refreshTokenPayload, jiraHeader)
          // save on disk
          def jsonResponse = readJSON text: jiraTokenPayload.content
          jiraAccessToken = jsonResponse.access_token
          def refreshToken = jsonResponse.refresh_token
          updateTokens(refreshToken, jiraRefreshToken)
          break
      default:
          println "No match found."
  }
}

def refreshTokensRequest (url, payload, header ){
//   println("payload: ${payload}  header: ${header}")
  def response = httpRequest acceptType: 'APPLICATION_JSON',
                    contentType: "APPLICATION_FORM",
                    customHeaders: header,
                    httpMode: 'POST',
                    requestBody: payload,
                    validResponseCodes: '200, 401, 403, 404',
                    consoleLogResponseBody: true,
                    url: url
    
    println(response)
    return response

}

def updateTokens(refreshToken, credentialId) {

  // Specify the new secret text
  def newSecretText = refreshToken

  // Access the Jenkins instance
  def jenkins = Jenkins.getInstance()

  // Access the global domain
  def globalDomain = Domain.global()

  // Find the existing credential by ID
  def existingCredential = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0]
      .getStore()
      .getCredentials(globalDomain)

  def credentialToUpdate = existingCredential.find { it.id == credentialId && it instanceof StringCredentials }

  if (credentialToUpdate) {
      // Update the credential with new secret text
      CredentialsStore store = Jenkins.getInstance().getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
      StringCredentials newCredential = new StringCredentialsImpl(credentialToUpdate.scope, credentialToUpdate.id, credentialToUpdate.description, Secret.fromString(newSecretText))
      
      // Remove the old credential
      store.removeCredentials(globalDomain, credentialToUpdate)
      
      // Add the updated credential
      store.addCredentials(globalDomain, newCredential)

      println("Credential updated successfully.")
  } else {
      println("Credential with ID ${tempoRefreshToken} not found.")
  }
}

def getPeriod(worklogDate) {
    // Define date format
    def sdf = new SimpleDateFormat("yyyy-MM-dd")
    // Set timezone
    sdf.setTimeZone(TimeZone.getTimeZone("GMT"))

    // Parse the input date string
    def date = sdf.parse(worklogDate)

    
    // Create a Calendar instance and set it to the provided date
    def cal = Calendar.getInstance()
    cal.time = date

    // Check if the input date is Sunday
    if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
        // If it's Sunday, set the calendar to the previous Monday
        cal.add(Calendar.DAY_OF_WEEK, -6)
    } else {
        // If it's not Sunday, set the calendar to the Monday of the same week
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    def monday = cal.time

    // Calculate Sunday of the week
    cal.add(Calendar.DAY_OF_WEEK, 6)
    def sunday = cal.time

    // Format the dates to desired format
    def mondayStr = sdf.format(monday)
    def sundayStr = sdf.format(sunday)

    return [mondayStr, sundayStr]
}



node {
    stage('Preparation') { 
        cleanWs()
        withCredentials([
            string(credentialsId: tempoClientId, variable: 'clientIdTempo'),
            string(credentialsId: tempoClientSecret, variable: 'clientSecretTempo'),
            string(credentialsId: jiraClientId, variable: 'clientIdJira'),
            string(credentialsId: jiraClientSecret, variable: 'clientSecretJira'),
            string(credentialsId: jiraCode, variable: 'codeJira')
        ]){
            //prepare payload for refresh tokens
            tempoRefreshBody = "grant_type=refresh_token&client_id=${clientIdTempo}&client_secret=${clientSecretTempo}&redirect_uri=https://enactor.co/&refresh_token="
            jiraRefreshBody = "grant_type=refresh_token&client_id=${clientIdJira}&client_secret=${clientSecretJira}&code=${codeJira}&redirect_uri=https://enactor.co/&refresh_token="
        }

    }
    stage('Import Worklogs') {
        withCredentials([
                    string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo'),
                    string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
                ]) 
        {
            boolean isRollback = false
            def timeSheetLogs = '''
                                    {
                                        "logs": []
                                    }
                                    '''
            def timeSheetLogJson  = readJSON(text: timeSheetLogs)
            
            if(!isDeleteProcess){
                println params.data;

                String[] lines = params.data.split('\n')
                String [] headers;
                int lineCount = 0;
                for(line in lines) {
                    retryCount  = 0 
                    if (lineCount == 0) {
                        headers = line.split(',');
                    }
                    else {
                        values = line.split(',');
                        def issueId = null
                        def userId = null
                        def leadId = null
                        
                        println("fetch Ticket Data")

                        def issueUrl = "https://api.atlassian.com/ex/jira/2eafded6-d1b9-41bd-8b84-6600f92e0032/rest/api/3/issue/${values[0]}"

                        def jiraRequestHeaders = [[
                                name: "Authorization",
                                value: "Bearer ${jiraAccessToken}"
                            ]]
                        def issueResponse = sendGetRequest(issueUrl, jiraRequestHeaders, "JIRA", "${jiraRefreshBody}${refreshTokenJira}" )
                        if(issueResponse.status == 200){
                        def issueJson  = readJSON(text: issueResponse.content)
                        issueId = issueJson.id
                        }

                        println("fetch User Data")
                        name = values[1].split(' ');
                        String encodedFirstName = URLEncoder.encode(name[0], "UTF-8");
                        String encodedLastName = URLEncoder.encode(name[1], "UTF-8"); 
                        String fullName =  encodedFirstName + encodedLastName
                        def userUrl = "https://api.atlassian.com/ex/jira/2eafded6-d1b9-41bd-8b84-6600f92e0032/rest/api/3/user/search?query=${fullName}&maxResults=1"

                        def userRequestHeaders = [[
                                name: "Authorization",
                                value: "Bearer ${jiraAccessToken}"
                            ]]
                        def userResponse = sendGetRequest(userUrl, userRequestHeaders, "JIRA", "${jiraRefreshBody}${refreshTokenJira}" )
                        if(userResponse.status == 200){
                            println("User Data : ${userResponse.content}")
                        def userJson  = readJSON(text: userResponse.content)
                        userId = userJson[0].accountId
                        }

                        println("Fetch lead data")
                        leadName = values[8].split(' ')
                        String encodedLeadFirstName = URLEncoder.encode(leadName[0], "UTF-8");
                        String encodedLeadLastName = URLEncoder.encode(leadName[1], "UTF-8"); 
                        String leadFullName =  encodedFirstName + encodedLastName
                        def leadUrl = "https://api.atlassian.com/ex/jira/2eafded6-d1b9-41bd-8b84-6600f92e0032/rest/api/3/user/search?query=${leadFullName}"

                        def leadRequestHeaders = [[
                                name: "Authorization",
                                value: "Bearer ${jiraAccessToken}"
                            ]]
                        def leadResponse = sendGetRequest(leadUrl, leadRequestHeaders, "JIRA", "${jiraRefreshBody}${refreshTokenJira}" )
                        if(leadResponse.status == 200){
                        def leadJson  = readJSON(text: leadResponse.content)
                        leadId = leadJson[0].accountId
                        }

                        //get period
                        def result = getPeriod(values[3])
                        println("Monday: ${result[0]}, Sunday: ${result[1]}")

                        def jsonRequest = """
                                            {
                                            "attributes": [
                                                {
                                                    "key": "_TempoAccount_",
                                                    "value": "${values[7]}"
                                                }
                                            ],
                                            "authorAccountId": "${userId}",
                                            "billableSeconds": ${values[6]},
                                            "description": "${values[2]}",
                                            "issueId": "${issueId}",
                                            "remainingEstimateSeconds": 3600,
                                            "startDate": "${values[3]}",
                                            "timeSpentSeconds": ${values[5]}
                                            }
                                        """
                        println jsonRequest;
                        def createWorklogUrl = "http://api.tempo.io/4/worklogs";


                        def requestHeaders = [[
                                                name: "Authorization",
                                                value: "Bearer ${tempoAccessToken}"
                                            ]]
                        def worklogResponse = sendPostRequest(createWorklogUrl, jsonRequest, requestHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}")
                        if(worklogResponse.status == 200 || worklogResponse.status == 201){
                            worklogResponseJSON  = readJSON(text: worklogResponse.content)
                            createdWorkLogs.add(worklogResponseJSON.tempoWorklogId)
                            println("Created worklog ids: ${createdWorkLogs}")
                            def logBody = '''
                                            {
                                            "lead": "",
                                            "user": "",
                                            "from": "",
                                            "to": "",
                                            }
                                        '''
                            def logJson  = readJSON(text: logBody)
                            logJson.lead = leadId
                            logJson.user = userId
                            logJson.from = result[0]
                            logJson.to = result[1]
                            timeSheetLogJson.logs.add(logJson)
                        }else{
                            isRollback = true
                        }
                    }
                    lineCount++;
                }
                
            }else{
                isRollback = true
                worklogKeys = params.data.split(', ')
                println("Worklog deleting keys: ${worklogKeys}")
                worklogKeys.each{key ->
                    createdWorkLogs.add(key)
                }
            }

            if(isRollback){
                println("Deleting worklogs")
                if(!createdWorkLogs.isEmpty()){
                    createdWorkLogs.each{ worklogId->
                        println("Deleting worklog: ${worklogId}")
                        String deleteWorklogUrl = "https://api.tempo.io/4/worklogs/${worklogId}"
                        def deleteRequestHeaders = [[
                                                name: "Authorization",
                                                value: "Bearer ${tempoAccessToken}"
                                                ],
                                                [
                                                name: "Content-Type",
                                                value: "application/json"
                                                ]]
                        deleteResponse = sendDeleteRequest(deleteWorklogUrl, deleteRequestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}" )
                    }
                }

            }else{

                // println("Submit worklogs")
                timeSheetLogJson.logs.each{log->
                    def submitBody = """
                                    {
                                    "comment": "",
                                    "reviewerAccountId": "${log.lead}"
                                    }
                                """
                    def submitlogUrl = "https://api.tempo.io/4/timesheet-approvals/user/${log.user}/submit?from=${log.from}&to=${log.to}";

                    def submitHeaders = [[
                                            name: "Authorization",
                                            value: "Bearer ${tempoAccessToken}"
                                        ]]
                    def submitResponse = sendPostRequest(submitlogUrl, submitBody, submitHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}")
                }
            }
            
            
            println("Final Logs: ${timeSheetLogJson}")

        }
    }

}