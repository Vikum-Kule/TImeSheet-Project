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

/*===================== External method loads =================== */
def svnHelper
def pipelineHelper
//credentials
String tempoClientId = 'TEMPO_CLIENT_ID'
String tempoClientSecret = 'TEMPO_CLIENT_SECRET'
String tempoCode = 'TEMPO_CODE'
String jiraClientId = 'JIRA_CLIENT_ID'
String jiraClientSecret = 'JIRA_CLIENT_SECRET'
String jiraCode = 'JIRA_CODE' 
String xeroClientId = 'XERO_CLIENT_ID'
String xeroClientSecret = 'XERO_CLIENT_SECRET'
Boolean isFinalInvoice = params.Final_Invoice_Generation

//tokens
tempoAccessToken = null
tempoRefreshToken = 'TEMPO_REFRESH_TOKEN'
jiraAccessToken = null
jiraRefreshToken = 'JIRA_REFRESH_TOKEN' 
xeroAccessToken = null
xeroRefreshToken = 'XERO_REFRESH_TOKEN'

Map stagesMap
String workspacePath
String sourceFolder = 'edtSource'
String sourceFolderPath
String scriptFolderPath
String jobExecutionNode = 'master'
teamResponse = null
mainJSON = null

def quoteList
workLogList = []
String tempoRefreshBody
String jiraRefreshBody
def xeroRefreshBody


def sendGetRequest(url, header, platform, refreshTokenPayload) {
    def response = httpRequest(url: url,
                               customHeaders: header ,
                               httpMode: 'GET',
                               validResponseCodes: '200, 401,403, 404')
    // Check if the request was successful or not
    if (response.status == 401){
      if (platform == "TEMPO"){
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

      }else if(platform == "XERO"){
          //update header with new access token
          refreshTokens(platform, refreshTokenPayload)
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${xeroAccessToken}"
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
    def response = httpRequest acceptType: 'APPLICATION_JSON',
                    contentType: 'APPLICATION_JSON',
                    customHeaders: header,
                    httpMode: 'POST',
                    requestBody: payload,
                    url: url
    if (response.status == 401){
      if(platform == "XERO"){
          //update header with new access token
          refreshTokens(platform, refreshTokenPayload)
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${xeroAccessToken}"
              }
          }
          sendPostRequest( url, payload, header, platform, refreshTokenPayload)
      }
    }else{
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
      case "XERO":
          println "Xero Refresh Token Update"
          String xeroRefreshUrl = 'https://identity.xero.com/connect/token?='
          def xeroHeader = [[name: "Content-Typen",value: 'application/x-www-form-urlencoded']]
          def xeroTokenPayload = refreshTokensRequest(xeroRefreshUrl, refreshTokenPayload, xeroHeader )
          // save on disk
          def jsonResponse = readJSON text: xeroTokenPayload.content
          xeroAccessToken = jsonResponse.access_token
          def refreshToken = jsonResponse.refresh_token
          updateTokens(refreshToken, xeroRefreshToken)
          break
      default:
          println "No match found."
  }
}

def refreshTokensRequest (url, payload, header ){
  println("payload: ${payload}  header: ${header}")
  def response = httpRequest acceptType: 'APPLICATION_JSON',
                    contentType: "APPLICATION_FORM",
                    customHeaders: header,
                    httpMode: 'POST',
                    requestBody: payload,
                    validResponseCodes: '200, 401, 404',
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

def organizeUserDetails(userId, refreshBody, refreshToken){
  String userBaseUrl = "https://api.atlassian.com/ex/jira/2eafded6-d1b9-41bd-8b84-6600f92e0032/rest/api/3/user?accountId="
   //update actor
   def jiraActorRequestHeaders = [[
           name: "Authorization",
           value: "Bearer ${jiraAccessToken}"
       ]]
   def userResponse = sendGetRequest("${userBaseUrl}${userId}", jiraActorRequestHeaders, "JIRA", "${refreshBody}${refreshToken}" )

   def userJson  = readJSON(text: userResponse.content)
   return userJson
}

def writeResponseToCSV(mainJSON) {
    def csvContentForValidWorklogs = new StringBuilder()
    def csvContentForInvalidWorklogs = new StringBuilder()
    
    // Add title to CSV content
    csvContentForValidWorklogs.append("WORK LOG ID, ISSUE ID, BILLABLE SECONDS,START DATE, DESCRIPTION, CREATED AT, UPDATED AT, STATUS, REVIEWER, USER, ACCOUNT KEY, TEAM \n")
    csvContentForInvalidWorklogs.append("WORK LOG ID, ISSUE ID, BILLABLE SECONDS,START DATE, DESCRIPTION, CREATED AT, UPDATED AT,STATUS, REVIEWER, USER, ACCOUNT KEY, TEAM, ERROR \n")

    // Iterate over results and append to CSV content
    mainJSON.teams.each { team ->
        team.timesheets?.each { timesheet ->
          timesheet.worklogs?.each{worklog ->
            def attributeVal = '-'
            worklog.attributes.values?.each{attribute ->
              if(attribute.key == '_TempoAccount_'){
                attributeVal = attribute.value 
              }
            }
              if(!worklog.errorLogs){
                csvContentForValidWorklogs.append("${worklog.tempoWorklogId},${worklog.issue.key},${worklog.billableSeconds},${worklog.startDate},${worklog.description},${worklog.createdAt},${worklog.updatedAt},${timesheet.status.key},${timesheet.reviewer.displayName},${timesheet.user.displayName},${attributeVal},${team.name}\n")
              }
              else{
                csvContentForInvalidWorklogs.append("${worklog.tempoWorklogId},${worklog.issue.key},${worklog.billableSeconds},${worklog.startDate},${worklog.description},${worklog.createdAt},${worklog.updatedAt},${timesheet.status.key},${timesheet.reviewer.displayName},${timesheet.user.displayName},${attributeVal},${team.name},${worklog.errorLogs}\n")
              }
                
          }         
        }
    }
    // Write content to CSV file
    writeFile file: "timeSheet.csv", text: csvContentForValidWorklogs.toString()
    writeFile file: "audit.csv", text: csvContentForInvalidWorklogs.toString()
    archiveArtifacts 'timeSheet.csv'
    archiveArtifacts 'audit.csv'
}

node('master') {
  try {
    stage('Preparation') {
      cleanWs()
      withCredentials([
            string(credentialsId: tempoClientId, variable: 'clientIdTempo'),
            string(credentialsId: tempoClientSecret, variable: 'clientSecretTempo'),
            string(credentialsId: jiraClientId, variable: 'clientIdJira'),
            string(credentialsId: jiraClientSecret, variable: 'clientSecretJira'),
            string(credentialsId: jiraCode, variable: 'codeJira'),
            string(credentialsId: xeroClientId, variable: 'clientIdXero'),
            string(credentialsId: xeroClientSecret, variable: 'clientSecretXero')
      ]){
        //prepare payload for refresh tokens
        tempoRefreshBody = "grant_type=refresh_token&client_id=${clientIdTempo}&client_secret=${clientSecretTempo}&redirect_uri=https://enactor.co/&refresh_token="
        jiraRefreshBody = "grant_type=refresh_token&client_id=${clientIdJira}&client_secret=${clientSecretJira}&code=${codeJira}&redirect_uri=https://enactor.co/&refresh_token="
        xeroRefreshBody = "grant_type=refresh_token&client_id=${clientIdXero}&client_secret=${clientSecretXero}&refresh_token="
      }
    }
    stage('GetAllTeams'){
      String fetchTeamUrl = "https://api.tempo.io/4/teams?limit=50&offset=0"
      withCredentials([
            string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
      ]){

        def isNextAvailable = true;
        
        while(isNextAvailable){
          def requestHeaders = [[
                                  name: "Authorization",
                                  value: "Bearer ${tempoAccessToken}"
                              ]]
          teamResponse = sendGetRequest(fetchTeamUrl, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}" )
          if(teamResponse.status == 200){
            //append fetch teams to existing teams
            def teamJSON = readJSON(text: teamResponse.content)
            if(mainJSON == null){
              mainJSON = teamJSON
              //rename feilds
              mainJSON.teams = mainJSON.results
              mainJSON.remove('results')
              println("initial JSON: ${mainJSON.teams}")
            }else{
              mainJSON.teams.addAll(teamJSON.results)
              println("updated JSON: ${mainJSON.teams}")
            }

            // if available next url
            if(teamJSON.metadata.next){
              println("next url: ${teamJSON.metadata.next}")
              fetchTeamUrl = teamJSON.metadata.next
            }else{
              isNextAvailable= false
            } 
          }
          else{
           isNextAvailable= false 
          }
        }
        //get team members
        println("get team members")
        if(!mainJSON.teams.isEmpty()){
          mainJSON.teams.each{team->
          String fetchMemberUrl = "https://api.tempo.io/4/team-memberships/team/${team.id}"
          def requestHeaders = [[
                                 name: "Authorization",
                                 value: "Bearer ${tempoAccessToken}"
                                ]]
          memberResponse = sendGetRequest(fetchMemberUrl, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}" )
          if(memberResponse.status == 200){
            def memberJSON = readJSON(text: memberResponse.content)
            //remove unnecessary data
            memberJSON.results = memberJSON.results.each{member->
              member.remove('self')
              member.remove('from')
              member.remove('id')
              member.remove('commitmentPercent')
              member.remove('to')
              member.remove('team')
            }
            team.members = memberJSON.results
          }
        }
          }
      }
    }

    stage('FetchTimeSheets'){
      if(!mainJSON.teams.isEmpty()){
          withCredentials([
              string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo'),
              string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
            ]){
                //remove properties that are not needed
                mainJSON.remove('self')
                mainJSON.remove('metadata')

                mainJSON.teams.each { team ->
                  def teamId = team.id
                  def teamName = team.name

                   //fetched projectId and projectNames
                  println "Team ID: $teamId, team Name: $teamName"

                  //define timesheet feild
                  team.timesheets = []

                  // Calculate last Monday
                  Calendar cal = Calendar.getInstance()
                  //calculate last monday of the week
                  int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                  int daysToLastMonday = Calendar.MONDAY - dayOfWeek
                  
                  if (daysToLastMonday == 0) {
                      // Last week Monday
                      daysToLastMonday = -7 
                  }
                  
                  cal.add(Calendar.DAY_OF_YEAR, daysToLastMonday)
                  Date lastMonday = cal.getTime()
                  
                  cal.add(Calendar.DAY_OF_YEAR, 6)
                  Date lastSunday = cal.getTime()

                  // Format dates
                  SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
                  
                  //run loop to fetch last 8weeks(2months) timesheets
                  String startDate
                  String endDate
                  for(int i = 0; i < 8; i++) {
                    if(i==0){
                      startDate = sdf.format(lastMonday)
                      endDate = sdf.format(lastSunday)
                    }else{
                      // Now, calculate the previous Monday and Sunday based on lastMonday
                      cal.setTime(lastMonday) // Set calendar to last Monday
                      cal.add(Calendar.DAY_OF_YEAR, -7) // Go back one more week for previous Monday
                      Date previousMonday = cal.getTime()
                      cal.add(Calendar.DAY_OF_YEAR, 6) // Add 6 days to get to previous Sunday
                      Date previousSunday = cal.getTime()

                      startDate = sdf.format(previousMonday)
                      endDate = sdf.format(previousSunday)
                      lastMonday = previousMonday

                    }
                    
                    println("startDate : ${startDate} and EndDate : ${endDate}")
                    String fetchTimeSheetUrl = "https://api.tempo.io/4/timesheet-approvals/team/${teamId}?from=${startDate}&to=${endDate}"

                    def requestHeaders = [[
                                        name: "Authorization",
                                        value: "Bearer ${tempoAccessToken}"
                                    ]]
                    def timeSheetResponse = sendGetRequest(fetchTimeSheetUrl, requestHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}" )

                    if(timeSheetResponse){
                        if(timeSheetResponse.status == 200){
                          def timeSheetjson  = readJSON(text: timeSheetResponse.content)
                          //filter Approved Timesheets
                          timeSheetjson.results = timeSheetjson.results.findAll { timesheet ->
                                                    timesheet.status?.key == 'APPROVED'
                                                  }
                          println("timeSheet results: ${timeSheetjson.results}")
                          if(!timeSheetjson.results.isEmpty()){
                            team.timesheets.addAll(timeSheetjson.results)
                          }
                        }else{
                        }
                    } 

                  }
                }          
                
            }
      }     
    }
    stage('FetchUsers'){
      if(!mainJSON.teams.isEmpty()){
        withCredentials([
              string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
            ])
        {
          mainJSON.teams.each { team ->
                team.timesheets?.each { timesheet ->
                  //fetch actor details
                  def actorJSON= organizeUserDetails(timesheet.status.actor.accountId, jiraRefreshBody, refreshTokenJira)
                  timesheet.status.actor = actorJSON

                  //fetch user details
                  def userJSON = organizeUserDetails(timesheet.user.accountId, jiraRefreshBody, refreshTokenJira)
                  timesheet.user = userJSON
                  def userRole = team.members.findAll{member->
                      timesheet.user.accountId == member.member.accountId 
                  }
                  println("Filtered User Role : ${userRole}")
                  timesheet.user.role = userRole[0].role

                  //fetch reviewer details
                  def reviewerJSON = organizeUserDetails(timesheet.reviewer.accountId, jiraRefreshBody, refreshTokenJira)
                  timesheet.reviewer = reviewerJSON
                }
          }
  
      }
      }
    }

    stage('FetchWorklogs'){
      if(!mainJSON.teams.isEmpty()){
        withCredentials([
              string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
            ])
        {
          // Get current date/time
          Calendar cal = Calendar.getInstance()

          // Subtract one day to get yesterday
          cal.add(Calendar.DAY_OF_MONTH, -1)

          // Set timezone to UTC for formatting
          TimeZone tz = TimeZone.getTimeZone("UTC")
          SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
          sdf.setTimeZone(tz) // Set the timezone to UTC

          // Format yesterday's date
        //   String yesterdayDate = sdf.format(cal.getTime())
          String yesterdayDate = '2024-04-09'
          println("yesterday Date: ${yesterdayDate}")

          mainJSON.teams.each { team ->
                team.timesheets?.each { timesheet ->
                  String workLogUrl = timesheet.worklogs.self

                  def requestHeaders = [[
                                      name: "Authorization",
                                      value: "Bearer ${tempoAccessToken}"
                                  ]]
                  def worLogResponse = sendGetRequest(workLogUrl, requestHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}" )
                  if(worLogResponse){
                      if(worLogResponse.status == 200){
                        def workLogjson  = readJSON(text: worLogResponse.content)
                        //filetr worklogs within yesterday
                        workLogjson.results = workLogjson.results.findAll{ workLog ->
                              String createdDate = workLog.createdAt
                              String updatedDate = workLog.updatedAt

                              def dateOfCreatedDate = createdDate.split("T")[0]
                              def dateOfUpdatedDate = updatedDate.split("T")[0]

                              (dateOfCreatedDate == yesterdayDate || dateOfUpdatedDate == yesterdayDate)
                              }
                        timesheet.worklogs = workLogjson.results
                      }else{
                      }
                  }
                  
                }
                if(team.timesheets){
                  team.timesheets = team.timesheets.findAll{ timesheet ->
                    !timesheet.worklogs.isEmpty()
                  }
                }
          }

          println("remove duplicate worklogs")
          // Set to  track  worklog IDs
          Set worklogIdSet = new HashSet()
          def ignoreRoles = ['Member', 'Team Lead']
          mainJSON.teams.each { team ->
              team.timesheets.each { timesheet ->
                  def uniqueWorklogs = []
                  timesheet.worklogs.each { worklog ->
                  println("Work Log Id: ${worklog.tempoWorklogId} and Role ${timesheet.user.role.name}")
                      if (worklogIdSet.add(worklog.tempoWorklogId)) {
                        if(!ignoreRoles.contains(timesheet.user.role.name)){
                          uniqueWorklogs.add(worklog)
                        }else{
                          worklogIdSet.remove(worklog.tempoWorklogId)
                        }
                      }
                  }
                  // Update timesheet with unique worklogs
                  timesheet.worklogs = uniqueWorklogs
                  println("filtered WorkLogs: ${timesheet.worklogs}")
              }
          }
          
      }
      }
    }

    stage('FetchJiraTickets'){
      if(!mainJSON.teams.isEmpty()){
        withCredentials([
              string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
            ])
        {
          mainJSON.teams.each { team ->
                team.timesheets?.each { timesheet ->
                  timesheet.worklogs?.each{worklog ->
                    def issueUrl = "https://api.atlassian.com/ex/jira/2eafded6-d1b9-41bd-8b84-6600f92e0032/rest/api/3/issue/${worklog.issue.id}"

                    def jiraRequestHeaders = [[
                            name: "Authorization",
                            value: "Bearer ${jiraAccessToken}"
                        ]]
                    def issueResponse = sendGetRequest(issueUrl, jiraRequestHeaders, "JIRA", "${jiraRefreshBody}${refreshTokenJira}" )
                    if(issueResponse.status == 200){
                      def issueJson  = readJSON(text: issueResponse.content)
                      worklog.issue.key = issueJson.key
                      worklog.issue.summery = issueJson.fields.summary
                    }
                  }
                } 
          }
            def finalJson = JsonOutput.prettyPrint(mainJSON.toString())
        }
      }
    }

    stage('Fetch Quotes'){
      withCredentials([
              string(credentialsId: xeroRefreshToken, variable: 'refreshTokenXero')
            ])
        {
          String fetchQuoteUrl = "https://api.xero.com/api.xro/2.0/Quotes?Status=ACCEPTED"

          def requestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                                [name: "xero-tenant-id", value: "8652e9a4-0afe-40b5-8c25-a52da8287fb2"],
                               ]
          def quoteResponse = sendGetRequest(fetchQuoteUrl, requestHeaders, "XERO","${xeroRefreshBody}${refreshTokenXero}")
          if(quoteResponse.status == 200){
              quoteList = readJSON(text: quoteResponse.content)
              println("Quotes JSON: ${quoteList}")

          }
        }
    }

    stage('Update Invoices'){
        withCredentials([
              string(credentialsId: xeroRefreshToken, variable: 'refreshTokenXero')
            ])
        {
          mainJSON.teams.each { team ->
                team.timesheets?.each { timesheet ->
                  timesheet.worklogs?.each{worklog ->
                    worklog.errorLogs = []
                    def invoiceStructure = '''
                                      {
                                        "Invoices": [
                                          {
                                            "Type": "ACCREC",
                                            "Contact": {
                                              "ContactID": ""
                                            },
                                            "DueDateString": "",
                                            "InvoiceNumber": "",
                                            "Reference": null,
                                            "Status": "DRAFT",
                                            "LineAmountTypes": "Inclusive",
                                            "LineItems": [
                                              {
                                                "ItemCode": "",
                                                "Quantity": ""
                                              }
                                            ]
                                          }
                                        ]
                                      }
                                        '''
                      def invoicejson  = readJSON(text: invoiceStructure)
                      //calculate working hours
                      def workedhours = worklog.billableSeconds /3600
                      invoicejson.Invoices[0].LineItems[0].Quantity = workedhours
                      //set Item code
                      invoicejson.Invoices[0].LineItems[0].ItemCode = timesheet.user.role.name
                      
                            //assign itemCode
                      worklog.attributes.values?.each{attribute ->
                        println("Attribute value: ${attribute.value} Attribute Key : ${attribute.key}")
                        if(attribute.key == '_TempoAccount_' && attribute.value){
                          invoicejson.Invoices[0].Reference =  attribute.value
                          println("attribute.key ${attribute.key} and attribute.value ${attribute.value}")
                        }
                      }

                      // set DueDate
                      Calendar cal = Calendar.getInstance()
                      cal.add(Calendar.DAY_OF_MONTH, 31)

                      // Set timezone to UTC for formatting
                      TimeZone tz = TimeZone.getTimeZone("UTC")
                      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
                      sdf.setTimeZone(tz) // Set the timezone to UTC

                      // Format the date after 31 days from today
                      String nextMonthDate = sdf.format(cal.getTime())
                      println("Set due date: ${nextMonthDate}")
                      invoicejson.Invoices[0].DueDateString = nextMonthDate

                      if(invoicejson.Invoices[0].LineItems[0].ItemCode){

                        if(invoicejson.Invoices[0].Reference){
                          //check customers
                          def quoteListJSON = JsonOutput.prettyPrint(quoteList.toString()) 
                          println("Quote JSON : ${quoteListJSON}")
                          def xeroCustomerQuote = quoteList.Quotes.findAll{quote ->
                            (quote.Reference == invoicejson.Invoices[0].Reference)
                          }

                          if(!xeroCustomerQuote.isEmpty()){

                            //assign customer id
                            invoicejson.Invoices[0].Contact.ContactID = xeroCustomerQuote[0].Contact.ContactID

                            //get invoices
                            String fetchInvoicesUrl = "https://api.xero.com/api.xro/2.0/Invoices?Statuses=DRAFT"

                            def requestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                                                        [name: "xero-tenant-id", value: "8652e9a4-0afe-40b5-8c25-a52da8287fb2"],
                                                      ]
                            def invoicesResponse = sendGetRequest(fetchInvoicesUrl, requestHeaders, "XERO","${xeroRefreshBody}${refreshTokenXero}")
                            if(invoicesResponse.status == 200){
                              def invoicesJSON  = readJSON(text: invoicesResponse.content)
                              if(!invoicesJSON.Invoices.isEmpty()){
                                  invoicesJSON.Invoices = invoicesJSON.Invoices.findAll{ invoice ->
                                      (invoice.Reference !=null && invoice.Reference == invoicejson.Invoices[0].Reference)
                                  }
                                  if(!invoicesJSON.Invoices.isEmpty()){
                                            
                                    println("filtered invoice JSON: ${invoicesJSON.Invoices}")
                                    //if invoices are not empty
                                      boolean isItemCodeExist = false
                                      //check whether Item Code is exist
                                      invoicesJSON.Invoices.each{ invoice->
                                        invoice.LineItems.each{lineItem->
                                          println("check lineItem code lineItem.ItemCode: ${lineItem.ItemCode} invoicejson.LineItems[0].ItemCode :${invoicejson.Invoices[0].LineItems[0].ItemCode}")
                                          if(lineItem.ItemCode == invoicejson.Invoices[0].LineItems[0].ItemCode){
                                            println("item code exist lineItem: ${lineItem.Quantity}")
                                            lineItem.Quantity += workedhours
                                            println("Updated line Item Qty: ${lineItem.Quantity}")
                                                    
                                            isItemCodeExist = true
                                          }
                                        }
                                        if(!isItemCodeExist){
                                          println("Add lineItem: ${invoicejson.Invoices[0].LineItems[0]}")
                                          invoice.LineItems.add(invoicejson.Invoices[0].LineItems[0])
                                        }
                                      }
                                    println("Update Invoice Number: ${invoicesJSON.Invoices[0].InvoiceNumber}")
                                    invoicejson.Invoices[0].InvoiceNumber = invoicesJSON.Invoices[0].InvoiceNumber
                                    invoicejson.Invoices[0].LineItems = invoicesJSON.Invoices[0].LineItems
                                  }else{
                                    //if invoices are empty
                                    println("Invoices are epmty after filter: ${invoicejson}")
                                          invoicejson.Invoices[0].remove('InvoiceNumber')
                                  }
                              }else{
                                //if invoices are empty
                                  println("Invoices are epmty before filter: ${invoicejson}")
                                  invoicejson.Invoices[0].remove('InvoiceNumber')
                              }

                              //remove unnecessary fields
                              invoicejson.Invoices[0].LineItems.each{lineItem ->
                                lineItem.remove('LineAmount')
                                lineItem.remove('AccountCode')
                                lineItem.remove('TaxAmount')
                                lineItem.remove('LineAmount')
                                lineItem.remove('UnitAmount')
                                lineItem.remove('TaxType')
                              }

                              // add created invoice to existing invoices
                              def finalInvoice = JsonOutput.prettyPrint(invoicejson.toString()) 
                              println("final invoice JSON: ${finalInvoice}")
                                        
                              // POST invoices
                               String postInvoicesUrl = "https://api.xero.com/api.xro/2.0/Invoices"

                               def invoiceRequestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                                                     [name: "xero-tenant-id", value: "8652e9a4-0afe-40b5-8c25-a52da8287fb2"],
                                                   ]
                                    
                               sendPostRequest( postInvoicesUrl, finalInvoice, invoiceRequestHeaders, "XERO", "${xeroRefreshBody}${refreshTokenXero}")
                            }
                          }else{
                            println("NO MATCHING CUSTOMER")
                            worklog.errorLogs.add("NO MATCHING CUSTOMER")
                          }
                        }
                        else{
                          println "REFERENCE NOT FOUND"
                          worklog.errorLogs.add("REFERENCE NOT FOUND")
                        }
                      }else{
                        println "USER ROLE NOT FOUND"
                        worklog.errorLogs.add("USER ROLE NOT FOUND")
                      }
                  }
                }
          }
        }
  }

  stage('WriteToCSV') {
       if (!mainJSON.teams.isEmpty()) {
          def finalJson = JsonOutput.prettyPrint(mainJSON.toString())
           println("final result: ${finalJson}")
           writeResponseToCSV(mainJSON)
           println "Timesheet data written to CSV file"
       }
  }    

    currentBuild.result = 'SUCCESS'
  } catch (Exception err) {
    println 'Caught an error while running the build. Saving error log in the database.'
    echo err.toString()
    currentBuild.result = 'FAILURE'
    throw err
  } finally {
    action = 'FINISH_LAUNCHING'
    cleanWs()
  }
}

