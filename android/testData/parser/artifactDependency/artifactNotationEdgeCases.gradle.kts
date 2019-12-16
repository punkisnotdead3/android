dependencies {
  // Dependency without a version
  compile("com.cool.company:artifact")
  // Dependency with blank group
  compile(":gson-2.2.4:+")
  // Since this notation doesn't have a name is it invalid and Gradle will complain
  compile("invalid")
}
