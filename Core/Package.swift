// swift-tools-version: 6.0
//
// ForumCore — platform-independent logic of ForPDA Next.
// Foundation only: builds and tests on macOS, iOS and Linux (`swift test --package-path Core`).
//
import PackageDescription

let package = Package(
    name: "ForumCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "ForumCore", targets: ["ForumCore"])
    ],
    targets: [
        .target(name: "ForumCore"),
        .testTarget(name: "ForumCoreTests", dependencies: ["ForumCore"])
    ]
)
