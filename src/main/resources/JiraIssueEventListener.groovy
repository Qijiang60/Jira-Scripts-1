// R.Wangemann
// V1.0.4


import com.atlassian.greenhopper.service.sprint.Sprint
import com.atlassian.gzipfilter.org.apache.commons.lang.time.DurationFormatUtils
import com.atlassian.jira.bc.issue.link.DefaultRemoteIssueLinkService
import com.atlassian.jira.bc.project.component.ProjectComponent
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.event.issue.IssueEvent
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.ModifiedValue
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem
import com.atlassian.jira.issue.label.LabelManager
import com.atlassian.jira.issue.link.DefaultRemoteIssueLinkManager
import com.atlassian.jira.issue.link.RemoteIssueLink
import com.atlassian.jira.issue.link.RemoteIssueLinkBuilder
import com.atlassian.jira.issue.search.SearchProvider
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder
import com.atlassian.jira.jql.parser.JqlQueryParser
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.user.util.UserUtil
import com.atlassian.jira.web.bean.PagerFilter
import com.atlassian.crowd.embedded.api.User
import org.apache.log4j.Category





//method retrieves the current user
def getCurrentApplicationUser() {
    //determine current user

    //Security
    jac = ComponentAccessor.getJiraAuthenticationContext()

    def CurrentUser

    CurrentUser = jac.getUser()


    return CurrentUser
}

//this method creates a comment
def addComment(Issue issue, String myComment) {

    cmm = ComponentAccessor.getCommentManager()

    //cmm.create(issue,getCurrentApplicationUser(),myComment,true)
    cmm.create(issue,getCurrentApplicationUser(),myComment,true)


}


//this method gets the value of a customfield value by its name
def getCustomFieldValue(Issue issue, String myCustomField) {

    cfm = ComponentAccessor.getCustomFieldManager()

    CustomField customField = cfm.getCustomFieldObjectByName(myCustomField);
    return  (String)customField.getValue(issue);

}

// this method returns a customfield
def getCustomField(String myCustomFieldName) {

    cfm = ComponentAccessor.getCustomFieldManager()

    CustomField myCustomField = cfm.getCustomFieldObjectByName(myCustomFieldName);
    return  myCustomField

}


//this method gets a list of subtasks of an issue, retrieves their summary and checks if a defined one exists in this list.
def checkIfSubTaskSummaryExists(Issue issue, String mySummaryToBeChecked) {

    //we create a list of all subtasks for the active issue
    def subTasks = issue.getSubTaskObjects()


    //we create a list of all summaries of all found subtasks
    def subTasksSummaries = []

    subTasks.each {

        subTasksSummaries.add(it.getSummary())
    }

    //we check if in the list of summaries  o
    def checkResult  = subTasksSummaries.contains(mySummaryToBeChecked)

    return checkResult
}


//this method is responsible for the creation of subTask
def addSubTask(Issue issue, String subTaskName, String subTaskDescription) {


    //start customizing

    def issueTypeIdForStory = "10001"
    def issueTypeIdForSubTask = "10101"

    //end customizing






    //Instanzierung der factories
    isf = ComponentAccessor.getIssueFactory()

    //IssueFactory: we create her a generic issue
    def issueObject = isf.getIssue()

    issueObject.setProjectObject(issue.getProjectObject())

    //Possible IssueTypeValues are 10001 story, 10101 subtask, 10102 bug, 10000 epic
    // old value 5 ?
    issueObject.setIssueTypeId(issueTypeIdForSubTask)

    //getValues of current issue = parent
    issueObject.setParentId(issue.getId())
    issueObject.setSummary(subTaskName + ': user story ' + issue.getSummary())
    issueObject.setAssignee(issue.getAssignee())
    issueObject.setDescription(subTaskDescription)
    issueObject.setReporter(issue.getReporter())



    //here we check if the value for the summary of a subtasks has already been used. We do not want to have
    //two subtasks with the same value.
    def toBeCreatedSubTaskSummary = subTaskName + ': user story ' + issue.getSummary()
    checkResult = checkIfSubTaskSummaryExists(issue,toBeCreatedSubTaskSummary)

    // we only create our new SubTask if the the value of summary does not exist in any already defined subtask
    if (!checkResult) {

        //the issue gets created with the IssueMngr
        ism = ComponentAccessor.getIssueManager()

        //Security
        //jac = ComponentAccessor.getJiraAuthenticationContext()

        currentUser = getCurrentApplicationUser()

        subTask = ism.createIssueObject(currentApplicationUser, issueObject)


        //the created subtask is linked to the issue.This is done through the SubTaskMngr

        stm = ComponentAccessor.getSubTaskManager()
        stm.createSubTaskIssueLink(issue, subTask, currentApplicationUser)


        // disable the watcher using the WatcherManager
        wtm = ComponentAccessor.getWatcherManager()
        wtm.stopWatching(currentApplicationUser, subTask)

    }

}



//Method retrieves the Fixed Version name of the current issue
def getReleaseName(Issue issue){

   // MutableIssue myMutableIssue = (MutableIssue)issue;// Flag EV is necessary to be able to be triggered by an event / listener

    ArrayList myListReleases = (ArrayList)issue.getFixVersions()

    def release = ""

    if(myListReleases!=null){



        //we only consider getting the first item, even though more fix versions can be assigned to an issue
        release = (String)myListReleases[0]

    }

    if(release == null) { release = "-"}

    return release
}

// method retrieves the assigned sprint of an issue
def getSprintName(Issue issue){

    ArrayList<Sprint> listOfSprints = (ArrayList<Sprint>) ComponentAccessor.getCustomFieldManager().getCustomFieldObjectByName("Sprint").getValue(issue);

    def SprintName =""

   if(listOfSprints!=null){

       //we only consider getting the first sprint in the list, event though more sprints can be assigned to an issue
            SprintName = (String)listOfSprints[0].getName()
        }

   else {
        //do something else

    }

    return SprintName
}

def setCustomFieldValue(Issue issue, String myValueToSave, CustomField myCustomField){

    def MutableIssue myMutableIssue = (MutableIssue)issue

    myMutableIssue.setCustomFieldValue(myCustomField,myValueToSave)


    Map<String,ModifiedValue> modifiedFields = myMutableIssue.getModifiedFields()

    FieldLayoutItem myFieldLayoutItem = ComponentAccessor.getFieldLayoutManager().getFieldLayout(myMutableIssue).getFieldLayoutItem(myCustomField)

    DefaultIssueChangeHolder myDefaultIssueChangeHolder = new DefaultIssueChangeHolder()

    final ModifiedValue myModifiedValue = modifiedFields.get(myCustomField.getId())

    myCustomField.updateValue(myFieldLayoutItem,myMutableIssue,myModifiedValue,myDefaultIssueChangeHolder)

}


def setCustomFieldValueUserPicker(Issue issue, String userName, CustomField myCustomField){



    def MutableIssue myMutableIssue = (MutableIssue)issue

    UserUtil userUtil = ComponentAccessor.getUserUtil()
    ApplicationUser applicationUser = userUtil.getUserByName(userName)


    myMutableIssue.setCustomFieldValue(myCustomField,applicationUser)


    Map<String,ModifiedValue> modifiedFields = myMutableIssue.getModifiedFields()

    FieldLayoutItem myFieldLayoutItem = ComponentAccessor.getFieldLayoutManager().getFieldLayout(myMutableIssue).getFieldLayoutItem(myCustomField)

    DefaultIssueChangeHolder myDefaultIssueChangeHolder = new DefaultIssueChangeHolder()

    final ModifiedValue myModifiedValue = modifiedFields.get(myCustomField.getId())

    myCustomField.updateValue(myFieldLayoutItem,myMutableIssue,myModifiedValue,myDefaultIssueChangeHolder)

}

def setLabel(Issue issue, String newValue, String fieldName, Category log){

    //debugging
    log.debug("Entering setLabel for issue: " + issue.getKey() +" and field " + fieldName)
    long time= System.currentTimeMillis()



    LabelManager labelManager = ComponentAccessor.getComponent(LabelManager.class)

    customFieldManager = ComponentAccessor.getCustomFieldManager()

    CustomField customField = customFieldManager.getCustomFieldObjectByName(fieldName)

    Set<String> set = convertToSetForLabels((String) newValue)


    log.debug("before update for field" + fieldName )
    labelManager.setLabels(getCurrentApplicationUser().getDirectoryUser(),issue.getId(),customField.getIdAsLong(),set,false,true)
    log.debug("after update for field " + fieldName )


    //debugging
    log.debug("Leaving setLabel for issue: " + issue.getKey() +" and field " + fieldName)
    long completedIn = System.currentTimeMillis() - time;
    log.debug("Setting label for issue :" + issue.getKey() +" and for field " + fieldName +" took: " + DurationFormatUtils.formatDuration(completedIn, "HH:mm:ss:SS"))

}

// sets n labels for a specific issue
def setLabels(Issue issue, Set labels, String fieldName,Category log){

    LabelManager labelManager = ComponentAccessor.getComponent(LabelManager.class)
    customFieldManager = ComponentAccessor.getCustomFieldManager()

    CustomField customField = customFieldManager.getCustomFieldObjectByName(fieldName)

    log.debug("before update of label for field "+ customField)
    labelManager.setLabels(getCurrentApplicationUser().getDirectoryUser(),issue.getId(),customField.getIdAsLong(),labels,false,true)
    log.debug("after update of label for field " + customField)
}


def convertToSetForLabels(String newValue){

    Set<String> set = new HashSet<String>();

    StringTokenizer st = new StringTokenizer(newValue," ")

    String myValue = ""


    while(st.hasMoreTokens()) {
        if(myValue == ""){

            myValue=myValue+st.nextToken()
        }

        else {
            myValue=myValue + "_" + st.nextToken()
        }


    }

    set.add(myValue)


    return set

}


// This method retrieves the issue based on its key
def getIssueByKey(String myIssueKey){


    Issue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(myIssueKey);

    return issue
}

def getComponentName(Issue myIssue){

    def MutableIssue myMutableIssue = (MutableIssue)myIssue

    ArrayList<ProjectComponent> myComponents = (ArrayList<ProjectComponent>)myMutableIssue.getComponentObjects()

    def myComponentName = ""

    if (myComponents!=null){

        //we only retrieve the first assigned component.
        myComponentName = (String)myComponents[0].getName()

    }


    return myComponentName

}

//get Object User for assignee of issue

def getAssigneeUserName(Issue myIssue){

    def MutableIssue myMutableIssue = (MutableIssue)myIssue

    def userName = myMutableIssue.getAssignee().getName()

    return userName
}




def getTodaysDate(){
    def today = new Date()
    return today.toString()
}

//retrieves the current issue i.e. for a listener
def getCurrentIssue(String flag){

    def myIssue

    if(flag == "WF"){
      myIssue =(Issue)issue
    }

    if(flag == "EV"){
        def event = event as IssueEvent
        myIssue = event.getIssue()
    }

    return myIssue
}




def getSprintAndReleaseName(Issue issue){

    def sprint = getSprintName(issue)

    def release = getReleaseName(issue)

    def sprintName

    if(sprint != "" && release != null){

        sprintName = release + "_" + sprint
    }

    if(sprint != "" && release == null) {

        sprintName = "_"+sprint
    }

    if(sprint == "" && release != null) {

        sprintName = "-"
    }

    // get rid of the blanks

    StringTokenizer st = new StringTokenizer(sprintName," ")

    String myValue = ""


    while(st.hasMoreTokens()) {
        if(myValue == ""){

            myValue=myValue+st.nextToken()
        }

        else {
            myValue=myValue + "_" + st.nextToken()
        }


    }

    sprintName = myValue

    return sprintName
}

//removes first and last character from a String
def removeFirstAndLastCharacterFromString(String myString){

    //remove last character
    myString = myString.substring(0, myString.length() - 1)

    //remove first character
    myString = myString.substring(1)

    return myString
}

def getAlmSubject(Issue issue){

    def sprint = getSprintName(issue)

    def release = getReleaseName(issue)

    def application_module = getCustomFieldValue(issue,".IT-App_Module")



    //we only have to remove the brackets if a value in the field ".IT-App_Module" is found
    if(application_module != null) {

        //Unfortunately the value from a customfield is within []
        //Therefore these two brackets have to be removed
        application_module = removeFirstAndLastCharacterFromString(application_module)

    }






    def almSubject = ""


    if(sprint != "" && release != null){

        almSubject = release + "_" + sprint
    }

    if(sprint != "" && release == null) {

        almSubject = "_"+sprint
    }

    if(sprint == "" && release != null) {

        almSubject = "-"
    }


    if(application_module != null) {

        almSubject = almSubject+"_"+application_module
    }






    // get rid of the blanks

    StringTokenizer st = new StringTokenizer(almSubject," ")

    String myValue = ""


    while(st.hasMoreTokens()) {
        if(myValue == ""){

            myValue=myValue+st.nextToken()
        }

        else {
            myValue=myValue + "_" + st.nextToken()
        }


    }

    almSubject = myValue

    return almSubject
}



def getIssuesOfNetwork(Issue issue,String traversalDepth,String linkType){

    def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
    def searchProvider = ComponentAccessor.getComponent(SearchProvider)
    def issueManager = ComponentAccessor.getIssueManager()
    def user = getCurrentApplicationUser()
    def issueId = issue.getKey()
    def query



// query for sub tasks without linktype

    if(linkType==""){

        query = jqlQueryParser.parseQuery("issueFunction in linkedIssuesOfRecursiveLimited(\"issue =" + issueId + "\"," + traversalDepth + ")  ORDER BY issuetype DESC")

    }

     else if(linkType=="relates_to"){

        query = jqlQueryParser.parseQuery("issueFunction in linkedIssuesOfRecursiveLimited(\"issue =" + issueId + "\"," + traversalDepth + ",\"relates to\")  ORDER BY issuetype DESC")

    }

    else if(linkType=="has_Epic"){

        query = jqlQueryParser.parseQuery("issueFunction in linkedIssuesOfRecursiveLimited(\"issue =" + issueId + "\"," + traversalDepth + ",\"has Epic\")  ORDER BY issuetype DESC")

    }

    else if(linkType=="is_Epic_of"){

        query = jqlQueryParser.parseQuery("issueFunction in linkedIssuesOfRecursiveLimited(\"issue =" + issueId + "\"," + traversalDepth + ",\"is Epic of\")  ORDER BY issuetype DESC")

    }


    else {
        query = jqlQueryParser.parseQuery("issueFunction in linkedIssuesOfRecursiveLimited(\"issue =" + issueId + "\"," + traversalDepth + ",\"" + linkType + "\")  ORDER BY issuetype DESC")
    }

    def issues = searchProvider.search(query, user, PagerFilter.getUnlimitedFilter())

    return issues


}





def getIssuesOfNetwork(Issue issue, String issueType,String traversalDepth,String linkType, Category log){

    def jqlQueryParser = ComponentAccessor.getComponent(JqlQueryParser)
    def searchProvider = ComponentAccessor.getComponent(SearchProvider)
    def issueManager = ComponentAccessor.getIssueManager()
    def user = getCurrentApplicationUser()
    def issueId = issue.getKey()
    def query

    log.debug("Entering getIssuesOfNetwork for issue  " + issue.getKey() +" looking for issuetype " + issueType + " and linktype "+ linkType)
    long time = System.currentTimeMillis()

// query for sub tasks without linktype

    if(linkType==""){

        query = jqlQueryParser.parseQuery("issueFunction in linkedIssuesOfRecursiveLimited(\"issue =" + issueId + "\"," + traversalDepth + ") AND issuetype =" + issueType + "  ORDER BY issuetype DESC")

    }

// query with consideration of linkType
    else {
        query = jqlQueryParser.parseQuery("issueFunction in linkedIssuesOfRecursiveLimited(\"issue =" + issueId + "\"," + traversalDepth + ",\"" + linkType + "\") AND issuetype =" + issueType + "  ORDER BY issuetype DESC")
    }

    def issues = searchProvider.search(query, user, PagerFilter.getUnlimitedFilter())


    //debugging
    log.debug("Leaving getIssuesOfNetwork for issue  " + issue.getKey() +" looking for issuetype " + issueType + " and linktype "+ linkType)
    long completedIn = System.currentTimeMillis() - time
    log.debug("getIssuesOfNetwork for issue " + issue.getKey() + "took "  + DurationFormatUtils.formatDuration(completedIn, "HH:mm:ss:SS"))


    return issues


}


def setReleaseAndSprintNamesInPKE(Issue issue,String customFieldName,log){

    //start customizing

    def issueTypeStory = "Story"
    def issueTypePKE = "PKE"
    def linkTypeRelatesTo = "relates to"

    //end customizing


    //we need the IssueManager in order to create the issue of the the result of the query
    IssueManager issueManager = ComponentAccessor.getIssueManager()

    //get all stories linked by relates to
    def stories = getIssuesOfNetwork(issue,issueTypeStory,"3",linkTypeRelatesTo,log).getIssues()

    //get the pke. We should only have one PKE linked to the stories
    def pkes = getIssuesOfNetwork(issue,issueTypePKE,"3",linkTypeRelatesTo,log).getIssues()

    //get the sprint name for each story. The names must be updated as labels in the pke

    Set<String> sprintNamesSet = new HashSet<String>()


    stories.each {

        //we create an issue
        def myIssue = issueManager.getIssueObject(it.getId())

        def mySprintAndReleaseName = getSprintAndReleaseName(myIssue)

        sprintNamesSet.add(mySprintAndReleaseName)

    }



    //we should have only one common business request for all found stories
    if (pkes.size()== 1){


        //we create an issue
        def myPKE = issueManager.getIssueObject(pkes.get(0).getId())

        setLabels(myPKE,sprintNamesSet,customFieldName)
    }


    // not nice
    println ""

    // end of new stuff
}


def setReleaseAndSprintNamesInBusinessRequest(Issue issue, String customFieldName,Category log){

    //start customizing

    def issueTypeStory = "Story"
    def issueTypeBusinessRequest = "\"Business Request\""
    def linkTypeRelatesTo = "relates to"

    //end customizing

    //debugging
    log.debug("Entering getReleaseAndSprintNamesInBusinessRequest: " + issue.getKey() +" and field " + customFieldName)
    long time= System.currentTimeMillis()



    //we need the IssueManager in order to create the issue of the the result of the query
    IssueManager issueManager = ComponentAccessor.getIssueManager()

    //get all stories linked by relates to
    def stories = getIssuesOfNetwork(issue,issueTypeStory,"3",linkTypeRelatesTo,log).getIssues()

    //get the business request. We should only have one business request linked to the stories
    def businessRequests= getIssuesOfNetwork(issue,issueTypeBusinessRequest,"3",linkTypeRelatesTo,log).getIssues()

    //get the sprint name for each story. The names must be updated as labels in the business request

    Set<String> sprintNamesSet = new HashSet<String>()


    stories.each {

        //we create an issue
        def myIssue = issueManager.getIssueObject(it.getId())

        def mySprintAndReleaseName = getSprintAndReleaseName(myIssue)

        sprintNamesSet.add(mySprintAndReleaseName)

    }



    //we should have only one common business request for all found stories
    if (businessRequests.size()== 1){


        //we create an issue
        def myBusinessRequest = issueManager.getIssueObject(businessRequests.get(0).getId())

        setLabels(myBusinessRequest,sprintNamesSet,customFieldName,log)
    }



    //debugging
    log.debug("Leaving getReleaseAndSprintNamesInBusinessRequest: " + issue.getKey() +" and field " + customFieldName)
    long completedIn = System.currentTimeMillis() - time;
    log.debug("getReleaseAndSprintNamesInBusinessRequest for issue :" + issue.getKey() +"took time: " + DurationFormatUtils.formatDuration(completedIn, "HH:mm:ss:SS"))



}

def updateComponents(Issue issue, Collection<ProjectComponent> components){

    IssueManager issueManager = ComponentAccessor.getIssueManager()

    MutableIssue mutableIssue = issue

    mutableIssue.setComponentObjects(components)

    User user = getCurrentApplicationUser().getDirectoryUser()

   issueManager.updateIssue(user,mutableIssue,com.atlassian.jira.event.type.EventDispatchOption.DO_NOT_DISPATCH,false)


}




// feature copy link to confluence from one issue to the linked issues
//


def syncExternalLinks(Issue issue){



    //start customizing

    def issueTypeStory = "Story"
    def issueTypeEpic = "Epic"
    def issueTypeBusinessRequest = "Business Request"
    def issueTypeRequirement = "Requirement"
    def issueTypeTestCase = "Test Case"
    def issueTypePKE = "PKE"

    //end customizing



    //we need the IssueManager in order to create the issue of the the result of the query
    IssueManager issueManager = ComponentAccessor.getIssueManager()


    def currentIssueType = issue.getIssueTypeObject().getName()


    //now we get all issues in the network with level 0 = current issue
    // consider max three levels in each direction of current issue
    // we don't limit the result based on type of relationship
    // we consider all types of issues

    def issuesInNetwork

    if(issue.getIssueTypeObject().getName()== issueTypeBusinessRequest){

        issuesInNetwork = getIssuesOfNetwork(issue,"3","").getIssues()
    }


    if(issue.getIssueTypeObject().getName()== issueTypePKE){

        issuesInNetwork = getIssuesOfNetwork(issue,"3","relates_to").getIssues()
    }



    if(issue.getIssueTypeObject().getName()== issueTypeStory){

        issuesInNetwork = getIssuesOfNetwork(issue,"2","relates_to").getIssues()
    }


    if(issue.getIssueTypeObject().getName()== issueTypeRequirement){

        issuesInNetwork = getIssuesOfNetwork(issue,"2","relates_to").getIssues()
    }

    if(issue.getIssueTypeObject().getName()== issueTypeTestCase){

        issuesInNetwork = getIssuesOfNetwork(issue,"3","relates_to").getIssues()
    }

    if(issue.getIssueTypeObject().getName()== issueTypeEpic){

        issuesInNetwork = getIssuesOfNetwork(issue,"2","is_Epic_of").getIssues()
    }
    //
    //sort all issues depending on their issueType

    List stories = []
    List epics = []
    List businessRequests = []
    List requirements = []
    List testCases = []
    List pkes = []

    List remainingIssueTypes = []





    for(Issue item : issuesInNetwork){

        def myIssue = issueManager.getIssueObject(item.getId())

        def myIssueType = item.getIssueTypeObject().getName()

        //we need the issueLinkMngr in order to be able to retrieve the internal links for all stories
        //as we only want to have those stories that have a relationship to an epic
        def issueLinkManager = ComponentAccessor.getIssueLinkManager()


        if (myIssue.getIssueTypeObject().getName()==issueTypeStory){

            stories.add(item)
        }

        else if (myIssue.getIssueTypeObject().getName() == issueTypeEpic){
            epics.add(item)
        }

        else if (myIssue.getIssueTypeObject().getName() == issueTypeBusinessRequest){
            businessRequests.add(item)
        }

        else if (myIssue.getIssueTypeObject().getName() == issueTypeRequirement){

            requirements.add(item)
        }

        else if (myIssue.getIssueTypeObject().getName() == issueTypeTestCase){

            testCases.add(item)
        }

        else if (myIssue.getIssueTypeObject().getName() == issueTypePKE){

            pkes.add(item)
        }



        else {

                remainingIssueTypes.add(item)
        }


        println "z"



    }

    configureSync(issue,businessRequests,epics,stories,requirements,testCases,pkes)

}



def configureSync(Issue issue,List businessRequests, List Epics, List stories, List requirements, List testCases, List pkes){


    //start customizing

    def issueTypeStory = "Story"
    def issueTypeEpic = "Epic"
    def issueTypeBusinessRequest = "Business Request"
    def issueTypeRequirement = "Requirement"
    def issueTypeTestCase = "Test Case"
    def issueTypePke = "PKE"

    //end customizing


    //now prepare the list of issues to which we have to sync the links depending on the issue type of the
    // current issue.


    def issueTypeOfCurrentIssue = issue.getIssueTypeObject().getName()

    //to all issues in this list we will have to sync the external links of the current issue
    List relevantIssuesToCopyLinksTo = []



    //If the current issue is of type business request, then we have to copy or delete the external links, which belong
    // to the business request to all linked stories, requirements and test cases.

    if (issueTypeOfCurrentIssue == issueTypeBusinessRequest){



       for (Issue item : stories){
           relevantIssuesToCopyLinksTo.add(item)
       }

        for (Issue item : requirements){
           relevantIssuesToCopyLinksTo.add(item)
       }


       for (Issue item : testCases){
           relevantIssuesToCopyLinksTo.add(item)
       }

        // we trigger here that all external links are copied or deleted



            copyAndDeleteExternalLinks(issue,relevantIssuesToCopyLinksTo)


    }


    else if (issueTypeOfCurrentIssue == issueTypePke){



        for (Issue item : stories){
            relevantIssuesToCopyLinksTo.add(item)
        }



        for (Issue item : requirements){
            relevantIssuesToCopyLinksTo.add(item)
        }


        for (Issue item : testCases){
            relevantIssuesToCopyLinksTo.add(item)
        }

        // we trigger here that all external links are copied or deleted



        copyAndDeleteExternalLinks(issue,relevantIssuesToCopyLinksTo)


    }

    else if (issueTypeOfCurrentIssue == issueTypeStory){


        //first we trigger to copy the process for the related higher level issues
        //be aware not to change the order. It is necessary to trigger the methods in this sorting order


        for(Issue item: businessRequests){
            syncExternalLinks(item)
        }


        for(Issue item: pkes){
            syncExternalLinks(item)
        }


        //-----------


        for (Issue item : requirements){
            relevantIssuesToCopyLinksTo.add(item)
        }


        for (Issue item : testCases){
            relevantIssuesToCopyLinksTo.add(item)
        }


            copyAndDeleteExternalLinks(issue,relevantIssuesToCopyLinksTo)




    }

    else if (issueTypeOfCurrentIssue == issueTypeRequirement){


        //and now we trigger to copy the process for the related higher level issues


        for(Issue item: stories){
            syncExternalLinks(item)
        }







        for (Issue item : testCases){
            relevantIssuesToCopyLinksTo.add(item)
        }


        copyAndDeleteExternalLinks(issue,relevantIssuesToCopyLinksTo)





    }

    else if (issueTypeOfCurrentIssue == issueTypeTestCase){




        //and now we trigger to copy the process for the related higher level issues



        for(Issue item: requirements){
            syncExternalLinks(item)
        }


    }



    else {
        println "z"
    }


}





def copyAndDeleteExternalLinks(Issue currentIssue, List<Issue> issuesToCopyLinksTo){




    try {

        def issueLinkManager = ComponentAccessor.getIssueLinkManager()

        def remoteIssueLinkManager = ComponentAccessor.getComponentOfType(DefaultRemoteIssueLinkManager.class)


        //get all remote links = external links for the current issue
        List sourceLinks = remoteIssueLinkManager.getRemoteIssueLinksForIssue(currentIssue)



        List sourceURLs =[]

        //create a list of all available sourceURLS
        //we need this list later in order to determine if we have to trigger a deletion

        if (sourceLinks.size() != 0) {


            for (RemoteIssueLink item : sourceLinks){

                def Url = item.getUrl()

                sourceURLs.add(Url)
            }

        }



        for (Issue itemInList : issuesToCopyLinksTo){


                //get the first issue for which we want the link to be copied to
                def newIssue = getIssueByKey(itemInList.getKey())



                //get all remote links = external links for the target issue
                List targetLinks = remoteIssueLinkManager.getRemoteIssueLinksForIssue(newIssue)


                //get for the target issue all existing URLS of the existing external links
                List targetURLsWithOriginCurrentIssue = []

                List allTargetURLs = []

                //create a list of all available targetURLS with origin current issue.
                // all other external links we do not touch

                if (targetLinks.size() != 0) {


                    for (RemoteIssueLink item : targetLinks){

                        def Url = item.getUrl()
                        def sourceID = item.getSummary()


                        //We  onl want to check links, which were created by this current issue
                        if(sourceID == currentIssue.getKey()){

                            targetURLsWithOriginCurrentIssue.add(Url)

                        }

                        allTargetURLs.add(Url)

                    }

                }


                //create a list for only the links that do not exist in the target issue
                //For this we check if in the list of all URLs existing in the target issue,
                //the URLs of all available links in the source issue exist.
                List relevantLinks = []

                for (RemoteIssueLink item : sourceLinks){

                        def found = allTargetURLs.find{it == item.getUrl()}

                        if (found == null) {

                            relevantLinks.add(item)
                        }

                }



                //create a list for those links, that have been deleted in the original issue (type story) and still exist
                //in the target issues. Remember, we want to have the links synchronized.


                List linksToBeDeletedInTargetIssue = []

                for (RemoteIssueLink item : targetLinks){

                    //here we check if current issue was origin for the external link in target issue
                    if (item.getSummary()==currentIssue.getKey()){

                        def found = sourceURLs.find{it == item.getUrl()}

                        if(found == null){

                            linksToBeDeletedInTargetIssue.add(item)
                        }


                    }

                }


                //In order to create, delete or update a link we need the a remoteIssueLinkService
                def remoteIssueLinkService = ComponentAccessor.getComponentOfType(DefaultRemoteIssueLinkService.class)


                //first we have to delete the links in the target issue with origin source issue, but don't exist there anymore.

                for (RemoteIssueLink item : linksToBeDeletedInTargetIssue) {

                    def deleteValidationResult = remoteIssueLinkService.validateDelete(getCurrentApplicationUser(),item.getId())

                    remoteIssueLinkService.delete(getCurrentApplicationUser(),deleteValidationResult)

                }




                //second, we want to create the missing links
                //For all relevant links we create an exact copy of every RemoteIssueLink
                //first we need to configure our link 1:1 to the existing ones.

                def linkBuilder = new RemoteIssueLinkBuilder()

                        for (RemoteIssueLink item : relevantLinks) {



                            //we create an exact copy of the existing link

                            //We only change the id of the issue
                            linkBuilder.issueId(newIssue.getId())
                            //We need the id of the source issue, just to be able to synchronize the links
                            //When a link is deleted in the source issue, the same link should be deleted in the target issue(s)
                            linkBuilder.summary(currentIssue.getKey())



                            //we copy the rest
                            linkBuilder.globalId(item.getGlobalId())
                            linkBuilder.title(item.getTitle())
                            linkBuilder.url(item.getUrl())
                            linkBuilder.iconUrl(item.getIconUrl())
                            linkBuilder.iconTitle(item.getIconTitle())
                            linkBuilder.relationship(item.getRelationship())
                            linkBuilder.applicationType(item.getApplicationType())
                            linkBuilder.applicationName(item.getApplicationName())

                            def newLink = linkBuilder.build()


                            //check if the issue already has got this link assigned to





                            def createValidationResult = remoteIssueLinkService.validateCreate(getCurrentApplicationUser(),newLink)

                            remoteIssueLinkService.create(getCurrentApplicationUser(),createValidationResult)

                        }//end for

        println "z"

        }
    }

    catch (all){

    }


}


def handleIssueUpdateAndAssignEvents(Issue issue, Category log){



    log.debug("Entering handleIssueUpdateAndAssignEvents() ")


    //begin customizing

            def customFieldNameRelease = ".Release"
            def customFieldNameSprint = ".Sprint"
            def customFieldNameSprintAndReleaseNames = ".Sprints"
            def customFieldNameDeveloper = ".Developer"
            def customFieldNameAlmSubject = ".Alm_Subject"
            def nameOfPrefix = "DEV"
            def issueTypeNameSubTasks = "Sub-task"
            def issueTypeNameStory = "Story"

    //end customizing
    def issueType = issue.getIssueTypeObject().getName()

    // These names should be standard in JIRA and not change from release to release
    def listOfFieldNames = ["Component", "Fix Version", "Sprint", "assignee"]
    def searchResult
    def field



    //def test = event.getChangeLog().getRelated('ChildChangeItem')

    for (item in listOfFieldNames) {


        log.debug("Entering event.getChangeLog().getRelated('ChildChangeItem') and looking for " + item)

        def check = event.getChangeLog().getRelated('ChildChangeItem').find { it.field == item }


        if(check != null) {

                searchResult = check
                field = searchResult.field



        log.debug("Found a change in field " + field +" containing following data: "+ searchResult)

        break

         }

        log.debug("No update found for field " + item)
    }


        //make sure, that all the subtasks assigned to a story allways have the same components assigned to them.
        //We need this functionality as the relationshipt between story and subtas is not a "relates to" relationship.
        //Therefore we can not use the plugin (Exocert" functionality to copy the value along the "relates to" relationship.
        if (searchResult != null && field == "Component") {


            Collection<ProjectComponent> myComponents = issue.getComponentObjects()


            if (issueType == issueTypeNameStory) {

                // get all SubTasks for the story
                def subTasks = getIssuesOfNetwork(issue,issueTypeNameSubTasks,"1","",log).getIssues()

                //we need the IssueManager in order to create the issue of the the result of the query
                IssueManager issueManager = ComponentAccessor.getIssueManager()


                subTasks.each {

                    //we create an issue
                    def myIssue = issueManager.getIssueObject(it.getId())

                    updateComponents(myIssue,myComponents)

                }


            }


        }//end of handling of components




        //if the field "fix version" or sprint was updated then, we copy the value to the defined customfields

        if (searchResult != null && field == "Fix Version" || field == "Sprint"){

            log.debug("Entering handling update of field: " + field)
            long time = System.currentTimeMillis()



            //copy the value to the customfields .Release and .Alm_Subject and .Sprint

            //copy to --> .Release
            setLabel(issue,getReleaseName(issue),customFieldNameRelease,log)


            //copy to --> .Sprint
            setLabel(issue,getSprintName(issue),customFieldNameSprint,log)

            //copy to --> .Alm_Subject
            setLabel(issue,getAlmSubject(issue),customFieldNameAlmSubject,log)


            //if the release is changed but the sprint remains - which should not really be the case
            //then we must make sure, that this change is also available for the relevant business requests
            setReleaseAndSprintNamesInBusinessRequest(issue,customFieldNameSprintAndReleaseNames,log)
            setReleaseAndSprintNamesInPKE(issue,customFieldNameSprintAndReleaseNames,log)

            log.debug("Leaving handling update of field: " + field)
            long completedIn = System.currentTimeMillis() - time;
            log.debug("Handling of an update of field :" + field +"took this time: " + DurationFormatUtils.formatDuration(completedIn, "HH:mm:ss:SS"))


        }



        if (searchResult != null && field == "assignee") {

            def issueSummary = issue.getSummary()
            //we get the first 3 characters of the summary in order the check if it is a DEV task
            def keyWord = issueSummary.substring(0,3)





            if(issueType == issueTypeNameSubTasks && keyWord == nameOfPrefix){

                def newAssignee = searchResult.newstring
                def username


                log.debug("NewAssignee=" + newAssignee)
                log.debug("issueType="+issueType)


                // set for this issue of type sub task the customfield .Developer t

                if(newAssignee != null) {

                    userName = getAssigneeUserName(issue)

                }

                if(newAssignee == null){
                    userName = ""
                }



                    //set for the parent issue of type story the customfield .Developer
                    //setLabel(issue,newAssignee,customFieldNameDeveloper,log)

                    setCustomFieldValueUserPicker(issue,userName,getCustomField(customFieldNameDeveloper))



                    // get my parent. For this look in the network of linked issues, from my point of view 1 level deep
                    // for a sub task the link type to its parent should be left blank
                    def queryResult = getIssuesOfNetwork(issue,issueTypeNameStory,"1","",log).getIssues()

                    //we need the IssueManager in order to create the issue of the the result of the query
                    IssueManager issueManager = ComponentAccessor.getIssueManager()

                    //every sub task should have only one parent
                    if (queryResult.size()== 1){


                        //we create an issue
                        def myIssue = issueManager.getIssueObject(queryResult.get(0).getId())

                        setCustomFieldValueUserPicker(myIssue,userName,getCustomField(customFieldNameDeveloper))
                    }



            }

            println ""
        }

        else {

/**

            //this method gets executed every time we get an issue update, issue create or issue assign event

            //copy to --> .Alm_Subject
            setLabel(issue,getAlmSubject(issue),customFieldNameAlmSubject)

            //sync links
            syncExternalLinks(issue)

            log.setLevel(org.apache.log4j.Level.OFF)

 */
        }





}



// READ ME:

//The development is based on JIRA 6.4.8.
//In case of an upgrade the script must also be upgraded. It is expected, that the code must be changed, as some methods do not exist anymore.


//The following methods MUST be customized according to the customizing of JIRA. The correct IssueTypeNames as defined in JIRA must be set
//-------------------------------------------
//addSubTask()
//setReleaseAndSprintNamesInBusinessRequest()
//setReleaseAndSprintNamesInPKE()
//syncExternalLinks()
//handleIssueUpdateAndAssignEvents()
//configureSync
//-------------------------------------------


//The following customfields must be set in order to enable the script to work properly
//  .Developer   .Sprint    .Release     .Sprints


//In order to retrieve the developer name of a sub-task the prefix of this sub-task must be set to "DEV"
//The prefix must be customized accordingly.

//The script can be triggered by an event or by a workflow.
//If the trigger should be an event, then the flag in the method getCurrentIssue() must be set to "EV"
//If the trigger is a workflow, then the flag in teh method getCurrentIssue() must be set to "WV"


//This is the method, that will be executed



def Category log = Category.getInstance("com.onresolve.jira.groovy")

log.setLevel(org.apache.log4j.Level.DEBUG)


handleIssueUpdateAndAssignEvents(getCurrentIssue("EV"),log)



