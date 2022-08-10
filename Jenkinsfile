#!groovy
@Library(['sparkPipeline', 'kubedPipeline', 'ciHelper@master']) _

node('SPARK_BUILDER_JAVA11') {

    try{
        // ***** CREDENTIALS USED IN IT-JENKINS *****
        // Bot to talk to Teams rooms
        env.PIPELINE_SPARK_BOT_API_TOKEN = "BeechNotifyBot"
        REGISTRY_CREDENTIALS = 'dhruva_ccc_bot'

        // ***** END CREDENTIALS *****

        // 'DSB Build & Deploy Notifications - spark room
        notifySparkRoomId = 'Y2lzY29zcGFyazovL3VzL1JPT00vMDc1NDVhZDAtMWYyMi0xMWViLWIxZjgtMzdmOGEzNjNhOGQ5'
    
        stage('Checkout') {
            cleanWs notFailBuild: true
            checkout scm
            sh 'ls -lrth'
        }
        stage('initializeEnv') {
            env.GIT_BRANCH = env.BRANCH_NAME ?: 'master'
            env.BUILD_NAME = env.GIT_BRANCH == 'master' ? env.BUILD_ID : env.GIT_BRANCH + "-" + env.BUILD_NUMBER
            env.GIT_COMMIT_FULL = sh(returnStdout: true, script: 'git rev-parse HEAD || echo na').trim()
            env.GIT_COMMIT_AUTHOR = sh(returnStdout: true, script: "git show -s --format='format:%ae' ${env.GIT_COMMIT_FULL} || echo na").trim()
            // TODO will be nice to have a BUILD_ID+TIMESTAMP+GIT_COMMIT_ID here instead of just BUILD_ID
            setBuildName(env.BUILD_NAME)
            if (env.CHANGE_AUTHOR && !env.CHANGE_AUTHOR_EMAIL) {
                env.CHANGE_AUTHOR_EMAIL = env.CHANGE_AUTHOR + '@cisco.com'
            }

            if (env.CHANGE_ID && !env.CHANGE_AUTHOR_EMAIL) {
                env.CHANGE_AUTHOR_EMAIL = env.GIT_COMMIT_AUTHOR
            }
            if (env.CHANGE_ID == null) { // CHANGE_ID will be null if there is no Pull Request
                // Announce in build notifications room
                notifyPipelineRoom("DSB: Build started.", roomId: notifySparkRoomId)
            } else {
                // 1:1 notification to PR owner
                notifyPipelineRoom("DSB: Build started.", toPersonEmail: env.CHANGE_AUTHOR_EMAIL)
            }
        }
         stage('buildAndDeploy') {
             withCredentials([file(credentialsId: 'SETTINGS_FILE', variable: 'settingsFile')]) {
                 currentBuild.result = 'SUCCESS'
                 sh """
                 env
                 /usr/share/maven/bin/mvn --settings $settingsFile clean deploy
                 """
                 // TODO sh 'java -jar dsb-common/target/dsb-common-1.0-SNAPSHOT.war'
                 step([$class: 'JacocoPublisher', changeBuildStatus: true, classPattern: '**/dsb-calling-app/server/target/classes/com/cisco,**/dsb-common/target/classes/com/cisco,**/dsb-connectivity-monitor/target/classes/com/cisco,**/dsb-proxy/dsb-proxy-service/target/classes/com/cisco,**/dsb-trunk/dsb-trunk-service/target/classes/com/cisco', execPattern: '**/target/**.exec', minimumInstructionCoverage: '1'])
             }
         }
         stage('postBuild') {
             // Report SpotBugs static analysis warnings (also sets build result on failure)
             def spotbugs = scanForIssues tool: spotBugs(pattern: '**/spotbugsXml.xml')
             publishIssues issues: [spotbugs]
             failBuildIfUnsuccessfulBuildResult("ERROR: Failed SpotBugs static analysis")
         }
         stage('archive') {
             archiveArtifacts artifacts: 'dsb-calling-app/server/microservice.yml', allowEmptyArchive: true
             archiveArtifacts artifacts: 'dsb-calling-app/server/docker/*', allowEmptyArchive: true
             archiveArtifacts artifacts: 'dsb-calling-app/antares-integration/docker/*', allowEmptyArchive: true
             archiveArtifacts artifacts: 'dsb-calling-app/server/target/dsb-calling-app-server-1.0-SNAPSHOT.war', allowEmptyArchive: true
             archiveArtifacts artifacts: '**/spotbugsXml.xml', allowEmptyArchive: true
         }

         if (env.GIT_BRANCH == 'master') {
             stage('Security Automation'){ 
                runSecurityScanJob()
             }
         }
         stage('build and push') {
             // build and push DSB application
             def tag = getTag()
             def buildArgs = [component: "dhruva", manifest: "manifest.yaml", tag: tag, metadata: getMetaData(tag)]
             dir('dsb-calling-app/server/'){
                 sh "cp target/*.war docker/"
                 sh "ls -lart docker/"
                 buildCI(this,buildArgs)
             }
             // build and push Test client
             buildArgs = [component: "dhruva-test-client", manifest: "manifest.yaml", tag: tag, metadata: getMetaDataTest(tag)]
             dir('dsb-calling-app/antares-integration'){
                 sh "cp target/*.jar docker/"
                 sh "ls -lart docker/"
                 buildCI(this,buildArgs)
             }
         }
    }
    catch (Exception ex) {
        currentBuild.result = 'FAILURE'
        echo "ERROR: Could not trigger the job "+ex.toString()
        throw ex
    }
    finally {
        def message
        def details = ''
        sh 'pwd'
        sh 'ls'
        if (currentBuild.result == 'FAILURE') {
            message = 'DSB: Build failed.'
        } else if (currentBuild.result == 'SUCCESS') {
            message = 'DSB: Build succeeded.'
        } else {
            message = "DSB: Build finished."
        }
        junit '**/target/surefire-reports/**/TEST-TestSuite.xml,**/target/failsafe-reports/**/TEST-TestSuite.xml'
        if (env.CHANGE_ID == null) {
            notifyPipelineRoom("$message $details", roomId: notifySparkRoomId)
        } else {
            notifyPipelineRoom("$message $details", toPersonEmail: env.CHANGE_AUTHOR_EMAIL)
        }
    } // end finally
}

def failBuildIfUnsuccessfulBuildResult(message) {
    // Check for UNSTABLE/FAILURE build result or any result other than "SUCCESS" (or null)
    this.checkoutPipeline()
    if (currentBuild.result != null && currentBuild.result != 'SUCCESS') {
        failBuild(message)
    }
}

def failBuild(message) {
    echo message
    throw new SparkException(message)
}

def getTag(){
    if (env.GIT_BRANCH == "master"){
        def TAG="2."+env.BUILD_NUMBER
        return TAG
    }
    if (env.CHANGE_ID != null){
        return env.CHANGE_ID+"-pr"
    }
    // this condition should not happen I think
    return "default"

}

def getMetaData(tag){
    if (env.GIT_BRANCH == 'master'){
        return {
                description = 'Dhruva-proxy for calling application'
                image_name = 'dhruva'
                operation_type = "QA-Done"
                image_tag = tag
                labels = '{"image_tag": "' + tag + '","environment": "dev","job": "metadata-service"}'
                registry_url = 'containers.cisco.com'
                service_group = 'WebEx'
            }

    }
    return {
        description = 'Dhruva-proxy for calling application'
        image_name = 'dhruva'
        operation_type = "Dev"
        image_tag = tag
        labels = '{"image_tag": "' + tag + '","environment": "dev","job": "metadata-service"}'
        registry_url = 'containers.cisco.com'
        service_group = 'WebEx'
    }

}

def getMetaDataTest(tag){
    if (env.GIT_BRANCH == 'master'){
        return {
            description = 'dhruva-test-client for dhruva-proxy calling app'
            image_name = 'dhruva-test-client'
            operation_type = "QA-Done"
            image_tag = tag
            labels = '{"image_tag": "' + tag + '","environment": "dev","job": "metadata-service"}'
            registry_url = 'containers.cisco.com'
            service_group = 'WebEx'
        }
    }
    return {
        description = 'dhruva-test-client for dhruva-proxy calling app'
        image_name = 'dhruva-test-client'
        operation_type = "Dev"
        image_tag = tag
        labels = '{"image_tag": "' + tag + '","environment": "dev","job": "metadata-service"}'
        registry_url = 'containers.cisco.com'
        service_group = 'WebEx'
    }
}