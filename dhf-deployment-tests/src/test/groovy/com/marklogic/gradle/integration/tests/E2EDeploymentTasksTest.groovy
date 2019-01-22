/*
 * Copyright 2012-2018 MarkLogic Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */

package com.marklogic.gradle.integration.tests

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.marklogic.gradle.tests.helper.BaseTest
import com.marklogic.hub.ApplicationConfig
import com.marklogic.hub.HubConfig
import com.marklogic.hub.impl.HubConfigImpl
import com.marklogic.mgmt.ManageClient
import com.marklogic.mgmt.ManageConfig
import com.marklogic.mgmt.api.API
import com.marklogic.mgmt.api.database.Database
import com.marklogic.mgmt.api.security.Privilege
import com.marklogic.mgmt.api.security.Role
import com.marklogic.mgmt.api.security.User
import com.marklogic.mgmt.api.server.Server
import com.marklogic.mgmt.resource.security.CertificateAuthorityManager
import com.marklogic.rest.util.ResourcesFragment

import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
import static org.gradle.testkit.runner.TaskOutcome.FAILED

class E2EDeploymentTasksTest extends BaseTest {

    @Shared def result

    @Shared File mlConfigDir = Paths.get(projectDir.toString(), HubConfig.USER_CONFIG_DIR).toFile()
    @Shared File mlConfigDbDir = Paths.get(mlConfigDir.toString(), "databases").toFile()
    @Shared File mlConfigServerDir = Paths.get(mlConfigDir.toString(), "servers").toFile()

    @Shared File hubConfigDir = Paths.get(projectDir.toString(), HubConfig.HUB_CONFIG_DIR).toFile()
    @Shared File hubConfigDbDir = Paths.get(hubConfigDir.toString(), "databases").toFile()
    @Shared File hubConfigServerDir = Paths.get(hubConfigDir.toString(), "servers").toFile()

    @Shared File mlPrivDir = Paths.get(mlConfigDir.toString(), "security", "privileges").toFile()
    @Shared File hubPrivDir = Paths.get(hubConfigDir.toString(), "security", "privileges").toFile()

    @Shared File mlRoleDir = Paths.get(mlConfigDir.toString(), "security", "roles").toFile()
    @Shared File hubRoleDir = Paths.get(hubConfigDir.toString(), "security", "roles").toFile()

    @Shared File mlUsersDir = Paths.get(mlConfigDir.toString(), "security", "users").toFile()
    @Shared File hubUsersDir = Paths.get(hubConfigDir.toString(), "security", "users").toFile()

    @Shared File mlCertAuthDir = Paths.get(mlConfigDir.toString(), "security", "certificate-authorities").toFile()
    @Shared File hubCertAuthDir = Paths.get(hubConfigDir.toString(), "security", "certificate-authorities").toFile()

    @Shared File mlCertTempDir = Paths.get(mlConfigDir.toString(), "security", "certificate-templates").toFile()
    @Shared File hubCertTempDir = Paths.get(hubConfigDir.toString(), "security", "certificate-templates").toFile()

    @Shared File mlProtectedPathDir = Paths.get(mlConfigDir.toString(), "security", "protected-paths").toFile()
    @Shared File mlQueryRoleSetDir = Paths.get(mlConfigDir.toString(), "security", "query-rolesets").toFile()

    @Shared File entitiesDir = Paths.get(projectDir.toString(), "plugins", "entities").toFile()
    @Shared File entityConfigDir = Paths.get(projectDir.toString(), HubConfig.ENTITY_CONFIG_DIR).toFile()

    @Shared File mlTriggersDir = Paths.get(projectDir.toString(), HubConfig.USER_CONFIG_DIR, "triggers").toFile()
    @Shared File hubTriggersDir = Paths.get(projectDir.toString(), HubConfig.HUB_CONFIG_DIR, "triggers").toFile()

    @Shared File mlSchemasDir = Paths.get(projectDir.toString(), "src", "main", "ml-schemas").toFile()

    @Shared File tmpDir = Paths.get(projectDir.toString(), ".tmp").toFile()

    @Shared API api

    def "test deploy a new database from ml-config with custom index" () {
        given:
        api = new API(getManageClient())
        File newDbConfig = Paths.get(mlConfigDbDir.toString(), "new-database-1.json").toFile()
        Database newDb = api.db(getPropertyFromPropertiesFile("mlNewDb1Name"))
        copyResourceToFile("ml-config/databases/new-database-1.json", newDbConfig)

        when:
        assert (newDb.getCollectionLexicon() == null)
        result = runTask('mlDeployDatabases')
        newDb = api.db(getPropertyFromPropertiesFile("mlNewDb1Name"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployDatabases').outcome == SUCCESS
        assert (newDb.getCollectionLexicon() == false)
        assert (getCustomDbRangePathIndexSize(getPropertyFromPropertiesFile("mlNewDb1Name")) == 1)
    }

    def "test deploy a final database from ml-config with custom index" () {
        given:
        api = new API(getManageClient())
        File newDbConfig = Paths.get(mlConfigDbDir.toString(), "final-database.json").toFile()
        Database newDb = api.db(getPropertyFromPropertiesFile("mlFinalDbName"))
        copyResourceToFile("ml-config/databases/final-database-index.json", newDbConfig)

        when:
        result = runTask('mlDeployDatabases')
        newDb = api.db(getPropertyFromPropertiesFile("mlFinalDbName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployDatabases').outcome == SUCCESS
        assert (getCustomDbRangePathIndexSize(getPropertyFromPropertiesFile("mlFinalDbName")) == 1)
        copyResourceToFile("ml-config/databases/final-database.json", newDbConfig)
    }

    def "test deploy staging database from hub-internal-config and ml-config" () {
        given:
        api = new API(getManageClient())
        File custStagingDbCongig = Paths.get(mlConfigDbDir.toString(), "staging-database.json").toFile()
        Database custStagingDb = api.db(getPropertyFromPropertiesFile("mlStagingDbName"))
        String stgSchemaDbName = getPropertyFromPropertiesFile("mlNewDb1Name")
        copyResourceToFile("ml-config/databases/staging-database.json", custStagingDbCongig)

        when:
        assert (custStagingDb.schemaDatabase == getPropertyFromPropertiesFile("mlStagingSchemasDbName"))
        result = runTask('mlDeployDatabases')
        custStagingDb = api.db(getPropertyFromPropertiesFile("mlStagingDbName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployDatabases').outcome == SUCCESS
        assert (custStagingDb.getSchemaDatabase().equals(stgSchemaDbName))
    }

    // TODO: Deploy new database from hub-internal-config and ml-config

    def "test deploy a new database from hub-internal-config" () {
        given:
        api = new API(getManageClient())
        File hubConfigNewDbDir = Paths.get(hubConfigDbDir.toString(), "new-database.json").toFile()

        Database newDb = api.db(getPropertyFromPropertiesFile("hubNewDb1Name"))
        String newDbName = getPropertyFromPropertiesFile("hubNewDb1Name")
        String newDbSchemaDbName = getPropertyFromPropertiesFile("mlNewDb1Name")

        copyResourceToFile("hub-internal-config/databases/new-database-1.json", hubConfigNewDbDir)

        when:
        assert (newDb.getSchemaDatabase() == null)
        result = runTask('mlDeployDatabases')
        newDb = api.db(getPropertyFromPropertiesFile("hubNewDb1Name"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployDatabases').outcome == SUCCESS
        assert (newDb.databaseName.equals(newDbName))
        assert (newDb.getSchemaDatabase().equals(newDbSchemaDbName))
    }

    def "test deploy a new server from ml-config" () {
        given:
        api = new API(getManageClient())
        File newServerConfig = Paths.get(mlConfigServerDir.toString(), "new-server-1.json").toFile()
        Server newServer = api.server(getPropertyFromPropertiesFile("mlNewAppserverName"))
        copyResourceToFile("ml-config/servers/new-server-1.json", newServerConfig)

        when:
        assert (newServer.address == null)
        result = runTask('mlDeployServers')
        newServer = api.server(getPropertyFromPropertiesFile("mlNewAppserverName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployServers').outcome == SUCCESS
        assert (newServer.getPort() == Integer.parseInt(getPropertyFromPropertiesFile("mlNewAppserverPort")))
        assert (newServer.serverName.equals(getPropertyFromPropertiesFile("mlNewAppserverName")))
        assert (newServer.groupName.equals("Default"))
    }

    def "test deploy staging server from hub-internal-config and ml-config" () {
        given:
        api = new API(getManageClient())
        File custStagingServerConfig = Paths.get(mlConfigServerDir.toString(), "staging-server.json").toFile()
        Server custStagingServer = api.server(getPropertyFromPropertiesFile("mlStagingAppserverName"))
        copyResourceToFile("ml-config/servers/staging-server.json", custStagingServerConfig)

        when:
        assert (custStagingServer.port == Integer.parseInt(getPropertyFromPropertiesFile("mlStagingPort")))
        result = runTask('mlDeployServers')
        custStagingServer = api.server(getPropertyFromPropertiesFile("mlStagingAppserverName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployServers').outcome == SUCCESS
        assert (custStagingServer.getPort() == Integer.parseInt(getPropertyFromPropertiesFile("mlStagingCustomPort")))
        assert (custStagingServer.serverName.equals(getPropertyFromPropertiesFile("mlStagingAppserverName")))
        assert (custStagingServer.contentDatabase.equals(getPropertyFromPropertiesFile("hubNewDb1Name")))
        assert (custStagingServer.groupName.equals("Default"))
    }

    def "test deploy staging server from hub-internal-config and ml-config without resource dbs in ml-config" () {
        given:
        api = new API(getManageClient())
        File custStagingServerConfig = Paths.get(mlConfigServerDir.toString(), "staging-server.json").toFile()
        Server custStagingServer = api.server(getPropertyFromPropertiesFile("mlStagingAppserverName"))
        copyResourceToFile("ml-config/servers/staging-server-2.json", custStagingServerConfig)

        when:
        assert (custStagingServer.port == Integer.parseInt(getPropertyFromPropertiesFile("mlStagingCustomPort")))
        result = runTask('mlDeployServers')
        custStagingServer = api.server(getPropertyFromPropertiesFile("mlStagingAppserverName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployServers').outcome == SUCCESS
        assert (custStagingServer.getPort() == Integer.parseInt(getPropertyFromPropertiesFile("mlStagingPort")))
        assert (custStagingServer.serverName.equals(getPropertyFromPropertiesFile("mlStagingAppserverName")))
        assert (custStagingServer.contentDatabase.equals(getPropertyFromPropertiesFile("mlStagingDbName")))
        assert (custStagingServer.groupName.equals("Default"))
    }

    def "test deploy a new server from hub-internal-config" () {
        given:
        api = new API(getManageClient())
        File newStagingServerConfig = Paths.get(hubConfigServerDir.toString(), "new-server.json").toFile()
        Server newStagingServer = api.server(getPropertyFromPropertiesFile("hubNewAppserverName"))
        copyResourceToFile("hub-internal-config/servers/new-server-1.json", newStagingServerConfig)

        when:
        assert (newStagingServer.port == null)
        result = runTask('mlDeployServers')
        newStagingServer = api.server(getPropertyFromPropertiesFile("hubNewAppserverName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployServers').outcome == SUCCESS
        assert (newStagingServer.getPort() == Integer.parseInt(getPropertyFromPropertiesFile("hubNewAppserverPort")))
        assert (newStagingServer.serverName.equals(getPropertyFromPropertiesFile("hubNewAppserverName")))
        assert (newStagingServer.contentDatabase.equals(getPropertyFromPropertiesFile("hubNewDb1Name")))
        assert (newStagingServer.groupName.equals("Default"))
    }

    def "test deploy privileges from ml-config" () {
        given:
        api = new API(getManageClient())
        File mlPrivilegeConfig = Paths.get(mlPrivDir.toString(), "privilege-1.json").toFile()
        Privilege mlPrivilege = api.privilegeExecute(getPropertyFromPropertiesFile("mlPrivilege1Name"))
        copyResourceToFile("ml-config/security/privileges/privilege-1.json", mlPrivilegeConfig)

        when:
        assert (mlPrivilege.getAction() == null)
        result = runTask('mlDeployPrivileges')
        mlPrivilege = api.privilegeExecute(getPropertyFromPropertiesFile("mlPrivilege1Name"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployPrivileges').outcome == SUCCESS
        assert (mlPrivilege.getAction().equals("urn:dhf-deployment-tests:privilege:1"))
        assert (mlPrivilege.getKind().equals("execute"))
    }

    def "test deploy privileges from hub-internal-config" () {
        given:
        api = new API(getManageClient())
        File hubPrivilegeConfig = Paths.get(hubPrivDir.toString(), "privilege-2.json").toFile()
        Privilege hubPrivilege = api.privilegeExecute(getPropertyFromPropertiesFile("hubPrivilege1Name"))
        copyResourceToFile("hub-internal-config/security/privileges/privilege-1.json", hubPrivilegeConfig)

        when:
        assert (hubPrivilege.getAction() == null)
        result = runTask('mlDeployPrivileges')
        hubPrivilege = api.privilegeExecute(getPropertyFromPropertiesFile("hubPrivilege1Name"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployPrivileges').outcome == SUCCESS
        assert (hubPrivilege.getAction().equals("urn:dhf-deployment-tests:privilege:2"))
        assert (hubPrivilege.getKind().equals("execute"))
    }

    def "test deploy privilege with same name and different config from hub-internal and ml-config, it should fail" () {
        given:
        api = new API(getManageClient())
        File hubPrivilegeConfig = Paths.get(hubPrivDir.toString(), "privilege-3.json").toFile()
        File mlPrivilegeConfig = Paths.get(mlPrivDir.toString(), "privilege-3.json").toFile()
        Privilege mlHubPrivilege = api.privilegeExecute(getPropertyFromPropertiesFile("mlHubPrivilege1Name"))
        copyResourceToFile("hub-internal-config/security/privileges/privilege-2.json", hubPrivilegeConfig)
        copyResourceToFile("ml-config/security/privileges/privilege-2.json", mlPrivilegeConfig)

        when:
        result = runFailTask('mlDeployPrivileges')
        mlHubPrivilege = api.privilegeExecute(getPropertyFromPropertiesFile("mlHubPrivilege1Name"))

        then:
        notThrown(UnexpectedBuildSuccess)
        result.task(':mlDeployPrivileges').outcome == FAILED
        result.output.contains('Error occurred while sending PUT request')
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "privileges", "privilege-3.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "privileges", "privilege-3.json"))
    }

    // TODO: mlDeploySecurity to deploy users roles and any certificates
    def "test deploy role from ml-config" () {
        given:
        api = new API(getManageClient())
        File mlRoleConfig = Paths.get(mlRoleDir.toString(), "ml-manager-role.json").toFile()
        copyResourceToFile("ml-config/security/roles/ml-manager-role.json", mlRoleConfig)
        Role mlRole = api.role(getPropertyFromPropertiesFile("mlRoleName"))

        when:
        assert (mlRole.role == null)
        result = runTask('mlDeployRoles')
        mlRole = api.role(getPropertyFromPropertiesFile("mlRoleName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployRoles').outcome == SUCCESS
        assert (mlRole.roleName.equals(getPropertyFromPropertiesFile("mlRoleName")))
        assert (mlRole.role.contains("manage-admin"))
    }

    def "test deploy role from hub-internal-config" () {
        given:
        api = new API(getManageClient())
        File hubRoleConfig = Paths.get(hubRoleDir.toString(), "hub-manager-role.json").toFile()
        copyResourceToFile("hub-internal-config/security/roles/hub-manager-role.json", hubRoleConfig)
        Role hubRole = api.role(getPropertyFromPropertiesFile("hubRoleName"))

        when:
        assert (hubRole.role == null)
        result = runTask('mlDeployRoles')
        hubRole = api.role(getPropertyFromPropertiesFile("hubRoleName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployRoles').outcome == SUCCESS
        assert (hubRole.roleName.equals(getPropertyFromPropertiesFile("hubRoleName")))
        assert (hubRole.role.contains("manage-admin"))
    }

    def "test deploy role from hub-internal-config and ml-config" () {
        given:
        api = new API(getManageClient())
        File hubRoleConfig = Paths.get(hubRoleDir.toString(), "comb-manager-role.json").toFile()
        File mlRoleConfig = Paths.get(mlRoleDir.toString(), "comb-manager-role.json").toFile()
        copyResourceToFile("hub-internal-config/security/roles/comb-manager-role.json", hubRoleConfig)
        copyResourceToFile("ml-config/security/roles/comb-manager-role.json", mlRoleConfig)
        Role combRole = api.role(getPropertyFromPropertiesFile("combRoleName"))

        when:
        assert (combRole.role == null)
        result = runTask('mlDeployRoles')
        combRole = api.role(getPropertyFromPropertiesFile("combRoleName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployRoles').outcome == SUCCESS
        assert (combRole.roleName.equals(getPropertyFromPropertiesFile("combRoleName")))
        assert (combRole.description.equals("ml-config description"))
        assert (combRole.role.contains("rest-admin"))
    }

    def "test deploy user from ml-config" () {
        given:
        api = new API(getManageClient())
        File mlUserConfig = Paths.get(mlUsersDir.toString(), "ml-project-manager.json").toFile()
        copyResourceToFile("ml-config/security/users/ml-project-manager.json", mlUserConfig)
        User mlUser = api.user(getPropertyFromPropertiesFile("mlNewUsername"))

        when:
        assert (mlUser.role == null)
        result = runTask('mlDeployUsers')
        mlUser = api.user(getPropertyFromPropertiesFile("mlNewUsername"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployUsers').outcome == SUCCESS
        assert (mlUser.userName.equals(getPropertyFromPropertiesFile("mlNewUsername")))
        assert (mlUser.role.contains(getPropertyFromPropertiesFile("mlRoleName")))
    }

    def "test deploy user from hub-internal-config" () {
        given:
        api = new API(getManageClient())
        File hubUserConfig = Paths.get(hubUsersDir.toString(), "hub-project-manager.json").toFile()
        copyResourceToFile("hub-internal-config/security/users/hub-project-manager.json", hubUserConfig)
        User hubUser = api.user(getPropertyFromPropertiesFile("hubNewUsername"))

        when:
        assert (hubUser.role == null)
        result = runTask('mlDeployUsers')
        hubUser = api.user(getPropertyFromPropertiesFile("hubNewUsername"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployUsers').outcome == SUCCESS
        assert (hubUser.userName.equals(getPropertyFromPropertiesFile("hubNewUsername")))
        assert (hubUser.role.contains(getPropertyFromPropertiesFile("hubRoleName")))
    }

    def "test deploy user from hub-internal-config and ml-config" () {
        given:
        api = new API(getManageClient())
        File hubUserConfig = Paths.get(hubUsersDir.toString(), "comb-project-manager.json").toFile()
        File mlUserConfig = Paths.get(mlUsersDir.toString(), "comb-project-manager.json").toFile()
        copyResourceToFile("hub-internal-config/security/users/comb-project-manager.json", hubUserConfig)
        copyResourceToFile("ml-config/security/users/comb-project-manager.json", mlUserConfig)
        User combUser = api.user(getPropertyFromPropertiesFile("combNewUsername"))

        when:
        assert (combUser.role == null)
        result = runTask('mlDeployUsers')
        combUser = api.user(getPropertyFromPropertiesFile("combNewUsername"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployUsers').outcome == SUCCESS
        assert (combUser.userName.equals(getPropertyFromPropertiesFile("combNewUsername")))
        assert (combUser.role.contains(getPropertyFromPropertiesFile("combRoleName")))
        assert (combUser.description.equals("A user from mlconfig"))
    }

    def "test deploy Certificate Authorities from ml-config" () {
        given:
        File mlCertAuthConfig = Paths.get(mlCertAuthDir.toString(), "server.crt").toFile()
        copyResourceToFile("ml-config/security/certificate-authorities/server.crt", mlCertAuthConfig)
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlSecurityUsername"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlSecurityPassword"))
        hubConfig().refreshProject(p, true)

        when:
        result = runTask('mlDeployCertificateAuthorities')
        ManageClient m = hubConfig().getManageClient()
        ManageConfig mc = m.manageConfig
        mc.setUsername("admin")
        mc.setPassword("admin")
        m.setManageConfig(mc)
        ResourcesFragment rf = new ResourcesFragment(m.getXml("/manage/v2/certificate-authorities"))
        int size = rf.getResourceCount()
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlHubAdminUserName"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlHubAdminUserPassword"))
        hubConfig().refreshProject(p, true)

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployCertificateAuthorities').outcome == SUCCESS
        assert (size == 70)

    }

    def "test deploy Certificate Authorities from hub-internal-config" () {
        given:
        File hubCertAuthConfig = Paths.get(hubCertAuthDir.toString(), "serverhub.crt").toFile()
        copyResourceToFile("hub-internal-config/security/certificate-authorities/server.crt", hubCertAuthConfig)
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlSecurityUsername"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlSecurityPassword"))
        hubConfig().refreshProject(p, true)

        when:
        result = runTask('mlDeployCertificateAuthorities')
        ManageClient m = hubConfig().getManageClient()
        ManageConfig mc = m.manageConfig
        mc.setUsername("admin")
        mc.setPassword("admin")
        m.setManageConfig(mc)
        ResourcesFragment rf = new ResourcesFragment(m.getXml("/manage/v2/certificate-authorities"))
        int size = rf.getResourceCount()
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlHubAdminUserName"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlHubAdminUserPassword"))
        hubConfig().refreshProject(p, true)

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployCertificateAuthorities').outcome == SUCCESS
        assert (size == 71)
    }

    def "test deploy Certificate Templates from mlconfig directory" () {
        given:
        File mlCertTempConfig = Paths.get(mlCertTempDir.toString(), "mltemplate.xml").toFile()
        copyResourceToFile("ml-config/security/certificate-templates/mltemplate.xml", mlCertTempConfig)
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlSecurityUsername"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlSecurityPassword"))
        hubConfig().refreshProject(p, true)

        when:
        result = runTask('mlDeployCertificateTemplates')
        ManageClient m = hubConfig().getManageClient()
        ManageConfig mc = m.manageConfig
        mc.setUsername("admin")
        mc.setPassword("admin")
        m.setManageConfig(mc)
        ResourcesFragment rf = new ResourcesFragment(m.getXml("/manage/v2/certificate-templates"))
        int size = rf.getResourceCount()
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlHubAdminUserName"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlHubAdminUserPassword"))
        hubConfig().refreshProject(p, true)

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployCertificateTemplates').outcome == SUCCESS
        assert (size == 1)
    }

    def "test deploy Certificate Templates from hub-internal-config directory" () {
        given:
        File hubCertTempConfig = Paths.get(hubCertTempDir.toString(), "hubtemplate.xml").toFile()
        copyResourceToFile("hub-internal-config/security/certificate-templates/hubtemplate.xml", hubCertTempConfig)
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlSecurityUsername"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlSecurityPassword"))
        hubConfig().refreshProject(p, true)

        when:
        result = runTask('mlDeployCertificateTemplates')
        ManageClient m = hubConfig().getManageClient()
        ManageConfig mc = m.manageConfig
        mc.setUsername("admin")
        mc.setPassword("admin")
        m.setManageConfig(mc)
        ResourcesFragment rf = new ResourcesFragment(getManageClient().getXml("/manage/v2/certificate-templates"))
        int size = rf.getResourceCount()
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlHubAdminUserName"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlHubAdminUserPassword"))
        hubConfig().refreshProject(p, true)

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployCertificateTemplates').outcome == SUCCESS
        // 2 as one template is installed in previous test
        assert (size == 2)
    }

    def "test mlDeployProtectedPaths" () {
        given:
        File mlProtectedPath = Paths.get(mlProtectedPathDir.toString(), "01_pii-protected-paths.json").toFile()
        copyResourceToFile("ml-config/security/protected-paths/01_pii-protected-paths.json", mlProtectedPath)
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlSecurityUsername"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlSecurityPassword"))
        hubConfig().refreshProject(p, true)

        when:
        result = runTask('mlDeployProtectedPaths')
        ManageClient m = hubConfig().getManageClient()
        ManageConfig mc = m.manageConfig
        mc.setUsername("admin")
        mc.setPassword("admin")
        m.setManageConfig(mc)
        ResourcesFragment rf = new ResourcesFragment(getManageClient().getXml("/manage/v2/protected-paths"))
        int size = rf.getResourceCount()
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlHubAdminUserName"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlHubAdminUserPassword"))
        hubConfig().refreshProject(p, true)

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployProtectedPaths').outcome == SUCCESS
        assert (size == 1)
    }

    def "test mlDeployQueryRoleSets" () {
        given:
        File mlQueryRolePath = Paths.get(mlQueryRoleSetDir.toString(), "pii-reader.json").toFile()
        copyResourceToFile("ml-config/security/query-rolesets/pii-reader.json", mlQueryRolePath)
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlSecurityUsername"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlSecurityPassword"))
        hubConfig().refreshProject(p, true)

        when:
        result = runTask('mlDeployQueryRolesets')
        ManageClient m = hubConfig().getManageClient()
        ManageConfig mc = m.manageConfig
        mc.setUsername("admin")
        mc.setPassword("admin")
        m.setManageConfig(mc)
        ResourcesFragment rf = new ResourcesFragment(getManageClient().getXml("/manage/v2/query-rolesets"))
        int size = rf.getResourceCount()
        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlHubAdminUserName"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlHubAdminUserPassword"))
        hubConfig().refreshProject(p, true)

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployQueryRolesets').outcome == SUCCESS
        assert (size == 1)
    }

    def "test laod all security files using mlDeploySecurity" () {
        given:
        File mlCertAuthConfig = Paths.get(mlCertAuthDir.toString(), "server1.crt").toFile()
        copyResourceToFile("ml-config/security/certificate-authorities/server1.crt", mlCertAuthConfig)

        File mlCertTempConfig = Paths.get(mlCertTempDir.toString(), "mltemplate1.xml").toFile()
        copyResourceToFile("ml-config/security/certificate-templates/mltemplate1.xml", mlCertTempConfig)

        File hubCertTempConfig = Paths.get(hubCertTempDir.toString(), "hubtemplate1.xml").toFile()
        copyResourceToFile("hub-internal-config/security/certificate-templates/hubtemplate1.xml", hubCertTempConfig)

        File mlProtectedPath = Paths.get(mlProtectedPathDir.toString(), "02_pii-protected-paths.json").toFile()
        copyResourceToFile("ml-config/security/protected-paths/02_pii-protected-paths.json", mlProtectedPath)

        File mlQueryRolePath = Paths.get(mlQueryRoleSetDir.toString(), "manage-reader.json").toFile()
        copyResourceToFile("ml-config/security/query-rolesets/manage-reader.json", mlQueryRolePath)

        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlSecurityUsername"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlSecurityPassword"))
        hubConfig().refreshProject(p, true)

        when:
        result = runTask('mlDeploySecurity')

        ManageClient m = hubConfig().getManageClient()
        ManageConfig mc = m.manageConfig
        mc.setUsername("admin")
        mc.setPassword("admin")
        m.setManageConfig(mc)

        ResourcesFragment rf = new ResourcesFragment(getManageClient().getXml("/manage/v2/query-rolesets"))
        int queryRoleSetsSize = rf.getResourceCount()

        rf = new ResourcesFragment(getManageClient().getXml("/manage/v2/protected-paths"))
        int protectedPathsSize = rf.getResourceCount()

        rf = new ResourcesFragment(getManageClient().getXml("/manage/v2/certificate-authorities"))
        int caCount = rf.getResourceCount()

        rf = new ResourcesFragment(getManageClient().getXml("/manage/v2/certificate-templates"))
        int ctCount = rf.getResourceCount()

        updatePropertiesFile("mlManageUsername", getPropertyFromPropertiesFile("mlHubAdminUserName"))
        updatePropertiesFile("mlManagePassword", getPropertyFromPropertiesFile("mlHubAdminUserPassword"))
        hubConfig().refreshProject(p, true)

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeploySecurity').outcome == SUCCESS
        assert (caCount == 72)
        assert (ctCount == 4)
        assert (protectedPathsSize == 2)
        assert (queryRoleSetsSize == 2)
    }

    /* Load Modules tests starts here*/
    def "test clear modules default database, this should remove modules from hub-core collection"() {
        given:
        int docCount = getModulesDocCount()
        int curCoreModCount = getModulesDocCount("hub-core-module")
        int diff = docCount - curCoreModCount

        when:
        result = runTask('mlClearModulesDatabase')
        int finalDocCount = getModulesDocCount()
        curCoreModCount = getModulesDocCount("hub-core-module")

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlClearModulesDatabase').outcome == SUCCESS
        assert (curCoreModCount == 0)
        assert (finalDocCount - curCoreModCount == diff)
    }

    def "test hubInstallModules to default database, this should add modules to hub-core collection"() {
        given:
        int docCount = getModulesDocCount()
        int coreModCount = getModulesDocCount("hub-core-module")
        int diff = docCount - coreModCount

        when:
        assert (coreModCount == 0)
        result = runTask('hubInstallModules')
        docCount = getModulesDocCount()
        coreModCount = getModulesDocCount("hub-core-module")

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':hubInstallModules').outcome == SUCCESS
        assert (docCount == coreModCount + diff)
        assert (coreModCount == hubCoreModCount)
    }

    def "test hubDeployUserModules"() {
        given:
        api = new API(getManageClient())
        clearDatabases(getPropertyFromPropertiesFile("mlStagingDbName"), getPropertyFromPropertiesFile("mlFinalDbName"))
        File testEntityDir = Paths.get(entitiesDir.toString(), "test").toFile()
        File useModDepFile = Paths.get(tmpDir.toString(), "user-modules-deploy-timestamps.properties").toFile()
        if(!testEntityDir.isDirectory()) {
            testEntityDir.mkdirs()
        }
        File testEntityConfig = Paths.get(testEntityDir.toString(), "test.entity.json").toFile()
        File stagingEntityConfig = Paths.get(entityConfigDir.toString(), "staging-entity-options.xml").toFile()
        File finalEntityConfig = Paths.get(entityConfigDir.toString(), "final-entity-options.xml").toFile()
        copyResourceToFile("plugin/test.entity.json", testEntityConfig)
        copyResourceToFile("entity-config/staging-entity-options.xml", stagingEntityConfig)
        copyResourceToFile("entity-config/final-entity-options.xml", finalEntityConfig)
        int stagingCount = getStagingDocCount()
        int finalCount = getFinalDocCount()
        int modCount = getModulesDocCount()

        when:
        result = runTask('hubDeployUserModules')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':hubDeployUserModules').outcome == SUCCESS
        assert (getStagingDocCount() == stagingCount + 1)
        assert (getFinalDocCount() == finalCount + 1)
        assert (getModulesDocCount() == modCount + 2)
        assert (useModDepFile.exists() == true)
    }

    def "test mlDeleteModuleTimestampsFile"() {
        given:
        File useModDepFile = Paths.get(tmpDir.toString(), "user-modules-deploy-timestamps.properties").toFile()
        copyResourceToFile("tmpDir/user-modules-deploy-timestamps.properties", useModDepFile)

        when:
        assert (useModDepFile.exists() == true)
        result = runTask('mlDeleteModuleTimestampsFile')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeleteModuleTimestampsFile').outcome == SUCCESS
        assert (useModDepFile.exists() == false)
    }

    def "test mlLoadModules"() {
        given:
        runTask('mlClearDatabase', '-Pdatabase=data-hub-STAGING', '-Pconfirm=true')
        runTask('mlClearDatabase', '-Pdatabase=data-hub-FINAL', '-Pconfirm=true')
        runTask('mlClearDatabase', '-Pdatabase=data-hub-MODULES', '-Pconfirm=true')
        File extMlLoadModConfig = Paths.get(projectDir.toString(), "src/main/ml-modules/ext/lib", "sample-lib.xqy").toFile()
        copyResourceToFile("ml-modules/ext/lib/sample-lib.xqy", extMlLoadModConfig)

        when:
        result = runTask('mlLoadModules')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlLoadModules').outcome == SUCCESS

        assert(getModulesDocCount("hub-core-module") == hubCoreModCount)
        // there will be 4 default docs also installed and ext/lib/sample-lib.xqy,
        // along with 109 hub-core-modules and 2 entity-options files. So adding 7 to verify
        assert(getModulesDocCount() == hubCoreModCount + 7)
        assert(getStagingDocCount() == 1)
        assert(getFinalDocCount() == 1)

    }

    def "test mlReloadModules"() {
        given:
        runTask('mlClearDatabase', '-Pdatabase=data-hub-STAGING', '-Pconfirm=true')
        runTask('mlClearDatabase', '-Pdatabase=data-hub-FINAL', '-Pconfirm=true')
        runTask('mlClearDatabase', '-Pdatabase=data-hub-MODULES', '-Pconfirm=true')
        File extMlLoadModConfig = Paths.get(projectDir.toString(), "src/main/ml-modules/ext/lib", "sample-lib.xqy").toFile()
        copyResourceToFile("ml-modules/ext/lib/sample-lib.xqy", extMlLoadModConfig)

        when:
        result = runTask('mlReloadModules')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlReloadModules').outcome == SUCCESS

        assert(getModulesDocCount("hub-core-module") == hubCoreModCount)
        // there will be 4 default docs also installed and ext/lib/sample-lib.xqy,
        // along with 109 hub-core-modules and 2 entity-options files. So adding 7 to verify
        assert(getModulesDocCount() == hubCoreModCount + 7)
        assert(getStagingDocCount() == 1)
        assert(getFinalDocCount() == 1)
    }

    def "deploy modules from multiple mlModulePaths" () {
        given:
        updatePropertiesFile("mlModulePaths", "src/main/ml-modules,src/test/ml-modules")
        File customModulePath = Paths.get(projectDir.toString(), "src/test/ml-modules/root/", "sample-mod.json").toFile()
        copyResourceToFile("ml-modules/sec/sample-mod.json", customModulePath)
        int modCount = getModulesDocCount()

        when:
        result = runTask('mlLoadModules')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlLoadModules').outcome == SUCCESS
        assert (getModulesDocCount() == modCount + 1)
    }

    def "deploy modules from multiple mlConfigPaths" () {
        given:
        api = new API(getManageClient())
        updatePropertiesFile("mlConfigPaths", "src/main/hub-internal-config,src/main/ml-config,src/test/ml-config")
        File customConfigPath = Paths.get(projectDir.toString(), "src/test/ml-config/security/users",
                "sample-config-user.json").toFile()
        copyResourceToFile("ml-modules/sec/sample-config-user.json", customConfigPath)
        User secUser = api.user(getPropertyFromPropertiesFile("mlSecConfUsername"))

        when:
        assert (secUser.role == null)
        result = runTask('mlDeployUsers')
        secUser = api.user(getPropertyFromPropertiesFile("mlSecConfUsername"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployUsers').outcome == SUCCESS
        assert (secUser.userName.equals(getPropertyFromPropertiesFile("mlSecConfUsername")))
        assert (secUser.role.contains(getPropertyFromPropertiesFile("mlHubUserRole")))
    }

    def "test deploy triggers from hub-internal-config directory" () {
        given:
        File hubTriggerConfig = Paths.get(hubTriggersDir.toString(), "my-trigger.json").toFile()
        copyResourceToFile("hub-internal-config/triggers/my-trigger.json", hubTriggerConfig)

        when:
        result = runTask('mlDeployTriggers')
        String getUri = "/manage/v2/databases/" + getPropertyFromPropertiesFile("mlFinalTriggersDbName") + "/triggers"
        println(getUri)
        ResourcesFragment rf = new ResourcesFragment(getManageClient().getXml(getUri))
        int size = rf.getResourceCount()

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployTriggers').outcome == SUCCESS
        assert (size == 1)
    }

    def "test deploy triggers from ml-config directory" () {
        given:
        File mlTriggerConfig = Paths.get(mlTriggersDir.toString(), "my-trigger-ml-config.json").toFile()
        copyResourceToFile("ml-config/triggers/my-trigger-ml-config.json", mlTriggerConfig)

        when:
        result = runTask('mlDeployTriggers')
        String getUri = "/manage/v2/databases/" + getPropertyFromPropertiesFile("mlFinalTriggersDbName") + "/triggers"
        ResourcesFragment rf = new ResourcesFragment(getManageClient().getXml(getUri))
        int size = rf.getResourceCount()

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployTriggers').outcome == SUCCESS
        assert (size == 2)
    }

    def "test deploy triggers from custom ml-config/databases/(name of triggers database)/triggers directory" () {
        given:
        File dbTriggerConfig = Paths.get(mlConfigDbDir.toString(), getPropertyFromPropertiesFile("mlFinalTriggersDbName"),
                "triggers").toFile()
        if(!dbTriggerConfig.isDirectory()) {
            dbTriggerConfig.mkdirs()
        }
        File dbTriggerFileConfig = Paths.get(dbTriggerConfig.toString(), "custom-trigger-ml-config.json").toFile()
        copyResourceToFile("ml-config/triggers/custom-trigger-ml-config.json", dbTriggerFileConfig)

        when:
        result = runTask('mlDeployTriggers')
        String getUri = "/manage/v2/databases/" + getPropertyFromPropertiesFile("mlFinalTriggersDbName") + "/triggers"
        ResourcesFragment rf = new ResourcesFragment(getManageClient().getXml(getUri))
        int size = rf.getResourceCount()

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployTriggers').outcome == SUCCESS
        assert (size == 3)
    }

    def "test deploy schemas from ml-schemas" () {
        given:
        File mlSchemasConfig = Paths.get(mlSchemasDir.toString(), "ml-sch.xsd").toFile()
        copyResourceToFile("ml-schemas/ml-sch.xsd", mlSchemasConfig)

        when:
        result = runTask('mlLoadSchemas')
        String rf = getManageClient().getJson("/manage/v2/databases/"+getPropertyFromPropertiesFile("mlFinalSchemasDbName")
                +"?view=counts").toString()
        ObjectMapper mapper = new ObjectMapper()
        JsonNode actualObj = mapper.readTree(rf)
        int docCount = actualObj.get("database-counts").get("count-properties").get("documents").get("value").asInt()

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlLoadSchemas').outcome == SUCCESS
        assert (docCount == 1)
    }
    
    def "test reDeploy schemas from ml-schemas" () {
        
    }
}
