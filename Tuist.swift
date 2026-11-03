import ProjectDescription

let tuist = Tuist(
    project: .tuist(
        compatibleXcodeVersions: .all,
        generationOptions: .options(
            optionalAuthentication: true
        ),
        cacheOptions: .options(
            storages: [.local]
        )
    )
)
