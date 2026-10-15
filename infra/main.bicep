@description('A unique name for this Azure Developer CLI environment.')
param environmentName string

@description('Primary Azure region for all resources.')
param location string

@description('MySQL administrator login.')
param dbAdminLogin string

@secure()
@description('MySQL administrator password.')
param dbAdminPassword string

@description('Application administrator login.')
param adminUsername string

@secure()
@description('Application administrator password.')
param adminPassword string

@secure()
@description('JWT signing secret. Use at least 32 random characters.')
param jwtSecret string

@secure()
@description('Optional Groq API key for AI postmortems.')
param groqApiKey string = ''

var resourceToken = toLower(uniqueString(subscription().id, resourceGroup().id, environmentName))
var acrName = take('shbs${resourceToken}', 50)
var mysqlName = take('shbsmysql${resourceToken}', 63)
var redisName = take('shbsredis${resourceToken}', 63)
var containerEnvironmentName = take('shbs-std-${environmentName}-${resourceToken}', 32)
var backendName = take('shbs-api-${environmentName}-${resourceToken}', 32)
var inventoryName = take('shbs-inventory2-${environmentName}-${resourceToken}', 32)
var frontendName = take('shbs-web-${environmentName}-${resourceToken}', 32)
var tags = {
  'azd-env-name': environmentName
  project: 'shbs'
}

resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: take('shbs-logs-${resourceToken}', 63)
  location: location
  tags: tags
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
  }
}

resource containerEnvironment 'Microsoft.App/managedEnvironments@2026-07-01' = {
  name: containerEnvironmentName
  location: location
  tags: tags
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalytics.properties.customerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
    environmentMode: 'WorkloadProfiles'
    workloadProfiles: [
      {
        name: 'Consumption'
        workloadProfileType: 'Consumption'
      }
    ]
  }
}

resource containerRegistry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  tags: tags
  sku: { name: 'Basic' }
  properties: {
    adminUserEnabled: false
  }
}

resource acrPullIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: take('shbs-acrpull-${resourceToken}', 128)
  location: location
  tags: tags
}

resource acrPullRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(containerRegistry.id, acrPullIdentity.id, 'AcrPull')
  scope: containerRegistry
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')
    principalId: acrPullIdentity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

resource mysql 'Microsoft.DBforMySQL/flexibleServers@2023-12-30' = {
  name: mysqlName
  location: location
  tags: tags
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    administratorLogin: dbAdminLogin
    administratorLoginPassword: dbAdminPassword
    version: '8.0.21'
    storage: {
      storageSizeGB: 20
      autoGrow: 'Enabled'
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: { mode: 'Disabled' }
    network: { publicNetworkAccess: 'Enabled' }
  }
}

resource mysqlDatabase 'Microsoft.DBforMySQL/flexibleServers/databases@2023-12-30' = {
  parent: mysql
  name: 'shbs'
  properties: {
    charset: 'utf8mb4'
    collation: 'utf8mb4_unicode_ci'
  }
}

// Container Apps use outbound Azure addresses. This permits Azure-hosted clients while TLS
// and the database credentials still protect the connection. Restrict this rule further when
// moving the environment to a VNet with private endpoints.
resource mysqlAzureServicesRule 'Microsoft.DBforMySQL/flexibleServers/firewallRules@2023-12-30' = {
  parent: mysql
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource redis 'Microsoft.Cache/redisEnterprise@2025-07-01' = {
  name: redisName
  location: location
  tags: tags
  properties: {
    minimumTlsVersion: '1.2'
    publicNetworkAccess: 'Enabled'
    highAvailability: 'Disabled'
    encryption: {}
  }
  sku: {
    name: 'Balanced_B0'
  }
}

resource redisDatabase 'Microsoft.Cache/redisEnterprise/databases@2025-07-01' = {
  parent: redis
  name: 'default'
  properties: {
    accessKeysAuthentication: 'Enabled'
    clientProtocol: 'Encrypted'
    clusteringPolicy: 'OSSCluster'
    evictionPolicy: 'VolatileLRU'
    modules: []
    port: 10000
  }
}

resource backend 'Microsoft.App/containerApps@2024-03-01' = {
  name: backendName
  location: location
  tags: union(tags, { 'azd-service-name': 'backend' })
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: { '${acrPullIdentity.id}': {} }
  }
  properties: {
    managedEnvironmentId: containerEnvironment.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: false
        targetPort: 8080
        transport: 'http'
      }
      registries: [
        {
          server: containerRegistry.properties.loginServer
          identity: acrPullIdentity.id
        }
      ]
      secrets: concat([
        { name: 'db-password', value: dbAdminPassword }
        { name: 'redis-password', value: redisDatabase.listKeys().primaryKey }
        { name: 'admin-password', value: adminPassword }
        { name: 'jwt-secret', value: jwtSecret }
      ], empty(groqApiKey) ? [] : [{ name: 'groq-api-key', value: groqApiKey }])
    }
    template: {
      containers: [
        {
          name: 'backend'
          image: 'mcr.microsoft.com/k8se/quickstart:latest'
          env: concat([
            { name: 'SPRING_PROFILES_ACTIVE', value: 'prod' }
            { name: 'DB_URL', value: 'jdbc:mysql://${mysql.properties.fullyQualifiedDomainName}:3306/shbs?useSSL=true&requireSSL=true&verifyServerCertificate=false' }
            { name: 'DB_USERNAME', value: dbAdminLogin }
            { name: 'DB_PASSWORD', secretRef: 'db-password' }
            { name: 'REDIS_HOST', value: '${redis.name}.${location}.redis.azure.net' }
            { name: 'REDIS_PORT', value: '10000' }
            { name: 'REDIS_USERNAME', value: 'default' }
            { name: 'REDIS_PASSWORD', secretRef: 'redis-password' }
            { name: 'SPRING_DATA_REDIS_SSL_ENABLED', value: 'true' }
            { name: 'INVENTORY_SERVICE_URL', value: 'http://${inventory.properties.configuration.ingress.fqdn}' }
            { name: 'ADMIN_USERNAME', value: adminUsername }
            { name: 'ADMIN_PASSWORD', secretRef: 'admin-password' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
          ], empty(groqApiKey) ? [] : [{ name: 'GROQ_API_KEY', secretRef: 'groq-api-key' }])
          resources: { cpu: json('0.5'), memory: '1Gi' }
          probes: [
            {
              type: 'Readiness'
              httpGet: { path: '/actuator/health/readiness', port: 8080 }
              initialDelaySeconds: 45
              periodSeconds: 15
            }
          ]
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 2 }
    }
  }
  dependsOn: [acrPullRoleAssignment]
}

resource inventory 'Microsoft.App/containerApps@2024-03-01' = {
  name: inventoryName
  location: location
  tags: union(tags, { 'azd-service-name': 'inventory2' })
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: { '${acrPullIdentity.id}': {} }
  }
  properties: {
    managedEnvironmentId: containerEnvironment.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: { external: false, targetPort: 8081, transport: 'http' }
      registries: [{
        server: containerRegistry.properties.loginServer
        identity: acrPullIdentity.id
      }]
      secrets: [
        { name: 'redis-password', value: redisDatabase.listKeys().primaryKey }
      ]
    }
    template: {
      containers: [
        {
          name: 'inventory'
          image: 'mcr.microsoft.com/k8se/quickstart:latest'
          env: [
            { name: 'REDIS_HOST', value: '${redis.name}.${location}.redis.azure.net' }
            { name: 'REDIS_PORT', value: '10000' }
            { name: 'REDIS_USERNAME', value: 'default' }
            { name: 'REDIS_PASSWORD', secretRef: 'redis-password' }
            { name: 'SPRING_DATA_REDIS_SSL_ENABLED', value: 'true' }
          ]
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 2 }
    }
  }
  dependsOn: [acrPullRoleAssignment]
}

resource frontend 'Microsoft.App/containerApps@2024-03-01' = {
  name: frontendName
  location: location
  tags: union(tags, { 'azd-service-name': 'frontend' })
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: { '${acrPullIdentity.id}': {} }
  }
  properties: {
    managedEnvironmentId: containerEnvironment.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: { external: true, targetPort: 8080, transport: 'http' }
      registries: [{
        server: containerRegistry.properties.loginServer
        identity: acrPullIdentity.id
      }]
      secrets: []
    }
    template: {
      containers: [
        {
          name: 'frontend'
          image: 'mcr.microsoft.com/k8se/quickstart:latest'
          env: [{ name: 'BACKEND_URL', value: 'http://${backend.properties.configuration.ingress.fqdn}' }]
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 2 }
    }
  }
  dependsOn: [acrPullRoleAssignment]
}

output AZURE_CONTAINER_REGISTRY_ENDPOINT string = containerRegistry.properties.loginServer
output BACKEND_URL string = 'https://${backend.properties.configuration.ingress.fqdn}'
output FRONTEND_URL string = 'https://${frontend.properties.configuration.ingress.fqdn}'
output MYSQL_SERVER_NAME string = mysql.name
output REDIS_CACHE_NAME string = redis.name
