#!groovy
@Library(['kubedPipeline', 'sparkPipeline']) _
node() {

    try{
        // ***** CREDENTIALS USED IN IT-JENKINS *****
        // Bot to talk to Teams rooms
        env.PIPELINE_SPARK_BOT_API_TOKEN = "BeechNotifyBot"

        // ***** END CREDENTIALS *****

        // 'Dhruva Build & Deploy Notifications - IT Jenkins' room
        notifySparkRoomId = 'Y2lzY29zcGFyazovL3VzL1JPT00vZjVmMDg3OTAtMDJjYi0xMWViLWJjNzktMTEwN2VhODIxNWY3'

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
        stage('build') {
            sh "ls -lrt"
            currentBuild.result = 'SUCCESS'
            sh 'mvn clean compile verify'
            sh 'ls'
            sh 'java -jar stub-app/target/stub-app-1.0-SNAPSHOT.jar'
        }
    }
    catch (Exception ex) {
                currentBuild.result = 'FAILURE'
                echo "ERROR: Could not trigger the job"
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
        if (env.CHANGE_ID == null) {
            notifyPipelineRoom("$message $details", roomId: notifySparkRoomId)
        } else {
            notifyPipelineRoom("$message $details", toPersonEmail: env.CHANGE_AUTHOR_EMAIL)
        }
    } // end finally
}
