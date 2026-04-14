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

import jenkins.model.Jenkins
import com.michelin.cio.hudson.plugins.rolestrategy.RoleBasedAuthorizationStrategy
import com.michelin.cio.hudson.plugins.rolestrategy.Role
import com.synopsys.arc.jenkins.plugins.rolestrategy.RoleType
import hudson.security.Permission

def instance = Jenkins.get()

// --- Permission sets ---

// Admin: Hudson.Administer covers everything — no need to enumerate sub-perms
Set<Permission> adminPerms = [
    Permission.fromId("hudson.model.Hudson.Administer"),
].toSet()

// Operator: run pipelines, read logs, cancel builds — no config or admin
Set<Permission> operatorPerms = [
    Permission.fromId("hudson.model.Hudson.Read"),
    Permission.fromId("hudson.model.Item.Read"),
    Permission.fromId("hudson.model.Item.Build"),
    Permission.fromId("hudson.model.Item.Cancel"),
    Permission.fromId("hudson.model.Item.Workspace"),
    Permission.fromId("hudson.model.Run.Update"),
    Permission.fromId("hudson.model.View.Read"),
].toSet()

// Viewer: read-only — can see jobs and builds but cannot trigger anything
Set<Permission> viewerPerms = [
    Permission.fromId("hudson.model.Hudson.Read"),
    Permission.fromId("hudson.model.Item.Read"),
    Permission.fromId("hudson.model.View.Read"),
].toSet()

// --- Role definitions ---
// Pattern ".*" applies the role to all jobs globally
def adminRole    = new Role("admin",    ".*", adminPerms,    "Full administrator access")
def operatorRole = new Role("operator", ".*", operatorPerms, "Run pipelines, read logs — no admin")
def viewerRole   = new Role("viewer",   ".*", viewerPerms,   "Read-only access to all jobs and views")

// --- Build and apply strategy ---
def strategy = new RoleBasedAuthorizationStrategy()
strategy.addRole(RoleType.Global, adminRole)
strategy.addRole(RoleType.Global, operatorRole)
strategy.addRole(RoleType.Global, viewerRole)

// Assign nnt-jkn-admin to admin role
// Add other users to operator/viewer via UI: Manage and Assign Roles → Assign Roles
strategy.assignRole(RoleType.Global, adminRole, "nnt-jkn-admin")

instance.setAuthorizationStrategy(strategy)
instance.save()

println "Done."
println "Roles configured: admin / operator / viewer"
println "nnt-jkn-admin → admin"
println "Add other users via: Manage Jenkins → Manage and Assign Roles → Assign Roles"
