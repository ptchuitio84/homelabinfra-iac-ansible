// =============================================================================
// jenkins/infra/configure_rbac.groovy
// =============================================================================
// PURPOSE:
// Configures Role-Based Authorization Strategy with 3 global roles:
//   admin    — full Jenkins control (Hudson.Administer implies all perms)
//   operator — run/cancel builds, read logs and workspaces — no admin/config
//   viewer   — read-only access to all jobs and views
//
// PREREQ: Role Strategy plugin must be installed and Jenkins restarted.
//
// USAGE:
//   Jenkins → Manage Jenkins → Script Console → paste and Run
//
// After running:
//   Manage Jenkins → Manage and Assign Roles → Assign Roles
//   to add/remove users from operator or viewer roles.
// =============================================================================

// NOTE: The com.synopsys.arc RoleType import location varies by plugin version.
// If this script throws MissingPropertyException, configure roles via the UI instead:
//   Manage Jenkins → Manage and Assign Roles → Manage Roles (pattern: .*)
//   Manage Jenkins → Manage and Assign Roles → Assign Roles

import jenkins.model.Jenkins
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy
import com.michelin.cio.hudson.plugins.rolestrategy.Role
import hudson.security.Permission

def instance = Jenkins.get()

// Role constructor signature (confirmed via reflection):
//   Role(String name, String pattern, Set<Permission> perms, String description)
// Build explicit HashSet<Permission> to avoid Groovy dispatch ambiguity.

def adminPerms = new HashSet<Permission>()
adminPerms.add(Permission.fromId("hudson.model.Hudson.Administer"))

def operatorPerms = new HashSet<Permission>()
["hudson.model.Hudson.Read", "hudson.model.Item.Read", "hudson.model.Item.Build",
 "hudson.model.Item.Cancel", "hudson.model.Item.Workspace",
 "hudson.model.Run.Update", "hudson.model.View.Read"].each { id ->
    operatorPerms.add(Permission.fromId(id))
}

def viewerPerms = new HashSet<Permission>()
["hudson.model.Hudson.Read", "hudson.model.Item.Read", "hudson.model.View.Read"].each { id ->
    viewerPerms.add(Permission.fromId(id))
}

// 4-arg constructor: (name, pattern, perms, description)
def adminRole    = new Role("admin",    ".*", adminPerms,    "Full administrator access")
def operatorRole = new Role("operator", ".*", operatorPerms, "Run pipelines, read logs — no admin")
def viewerRole   = new Role("viewer",   ".*", viewerPerms,   "Read-only access")

// Load RoleType dynamically — avoids import package variance between plugin versions
def roleTypeClass = Class.forName("com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType")
def globalType    = roleTypeClass.enumConstants.find { it.name() == "Global" }

def strategy = new RoleBasedAuthorizationStrategy()
strategy.addRole(globalType, adminRole)
strategy.addRole(globalType, operatorRole)
strategy.addRole(globalType, viewerRole)
strategy.assignRole(globalType, adminRole, "nnt-jkn-admin")

instance.setAuthorizationStrategy(strategy)
instance.save()

println "Done. Roles: admin / operator / viewer. nnt-jkn-admin → admin."
