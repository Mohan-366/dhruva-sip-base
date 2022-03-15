#!groovy
@Library(['kubedPipeline', 'sparkPipeline', 'ciHelper@master']) _
node() {

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
                 sh '''
                 env
                 ls -lrt
                 cp $settingsFile \$(pwd)/settings.xml
                 docker run \\
                 --mount type=bind,src="\$(pwd)"/settings.xml,dst=/src/settings.xml \\
                 --rm -v `pwd`:/opt/code -w /opt/code -e JAVA_VERSION=11 \\
                 containers.cisco.com/ayogalin/maven-builder:one \\
                 sh -c "/setenv.sh; java -version;/usr/share/maven/bin/mvn --settings /src/settings.xml clean deploy"
                 '''
                 //TODO sh 'java -jar dsb-common/target/dsb-common-1.0-SNAPSHOT.war'
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
             archiveArtifacts artifacts: 'dsb-calling-app/server/microservice-itjenkins.yml', allowEmptyArchive: true
             archiveArtifacts artifacts: 'dsb-calling-app/server/docker/*', allowEmptyArchive: true
             archiveArtifacts artifacts: 'dsb-calling-app/integration/docker/*', allowEmptyArchive: true
             archiveArtifacts artifacts: 'dsb-calling-app/server/target/dsb-calling-app-server-1.0-SNAPSHOT.war', allowEmptyArchive: true
         }
         stage('build and publish wbx3 images') {
             try {
                 if (env.GIT_BRANCH == 'master') {
                     sh 'ls -lrth'
                     def TAG="2."+env.BUILD_NUMBER
                     /* This is in WebexPlatform/pipeline. It reads dhruva's microservice.yml
                 to determine where to build and push (in our case, containers.cisco.com/edge_group)
                 */
                     // TODO will be nice to have a BUILD_ID+TIMESTAMP+GIT_COMMIT_ID here instead of just BUILD_NUMBER
                     // Since the existing pipeline currently uses
                     // dockerhub.cisco.com but the new build pipeline uses containers.cisco.com, we
                     // pass a file called microservice-itjenkins.yml in this case (which lets us handle
                     // both requirements for now).

                     buildAndPushWbx3DockerImages("dsb-calling-app/server/microservice-itjenkins.yml", TAG, REGISTRY_CREDENTIALS)
                 }
                 if (env.CHANGE_ID != null) {
                     def PULL_REQUEST = env.CHANGE_ID+'-pr'
                     buildAndPushWbx3DockerImages("dsb-calling-app/server/microservice-itjenkins.yml", PULL_REQUEST, REGISTRY_CREDENTIALS)
                 }

             } catch (Exception ex) {
                 echo "ERROR: Could not trigger the build and publish of dsb-calling-app docker image."
                 throw ex
             }
         }
        if (env.GIT_BRANCH == 'master') {
            stage('ecr sync') {
                def tag = "2."+env.BUILD_NUMBER
                //Pull dhruva image and get SHA of that image which will be artifactID
                sh "docker pull containers.cisco.com/edge_group/dhruva:${tag}"
                def artifactID = sh(
                        script: "docker inspect --format=\'{{.Id}}\' containers.cisco.com/edge_group/dhruva:${tag} | cut -f 2 -d \':\'",
                        returnStdout: true
                ).trim()
                sh "docker pull containers.cisco.com/edge_group/dhruva-test-client:${tag}"
                def testartifactID = sh(
                        script: "docker inspect --format=\'{{.Id}}\' containers.cisco.com/edge_group/dhruva-test-client:${tag} | cut -f 2 -d \':\'",
                        returnStdout: true
                ).trim()
                try {
                    def metaBody = {
                        artifact_id = artifactID
                        description = 'DSB'
                        image_name = 'dhruva'
                        operation_type = "Int"
                        image_tag = tag
                        labels = '{"image_tag": "' + tag + '","environment": "dev","job": "metadata-service"}'
                        registry_url = 'containers.cisco.com'
                        service_group = 'WebEx'
                    }
                    def buildArgs = [component: "dhruva", manifest: "dsb-calling-app/server/manifest.yaml", tag: tag, metadata: metaBody]
                    buildCI(this, buildArgs)
                    def testmetaBody = {
                        artifact_id = testartifactID
                        description = 'dhruva-test-client'
                        image_name = 'dhruva-test-client'
                        operation_type = "Int"
                        image_tag = tag
                        labels = '{"image_tag": "' + tag + '","environment": "dev","job": "metadata-service"}'
                        registry_url = 'containers.cisco.com'
                        service_group = 'WebEx'
                    }
                    def testbuildArgs = [component: "dhruva-test-client", manifest: "dsb-calling-app/integration/manifest.yaml", tag: tag, metadata: testmetaBody]
                    buildCI(this, testbuildArgs)
                    sh "curl https://ecr-sync.int.mccprod02.prod.infra.webex.com/api/v1/sync"
                } catch (Exception e) {
                    echo "ERROR: An error occurred while syncing images to ECR"
                    throw e
                }
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
    if (currentBuild.result != null && currentBuild.result != 'SUCCESS') {
        failBuild(message)
    }
}

def failBuild(message) {
    echo message
    throw new SparkException(message)
}