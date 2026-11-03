//
//  AttachmentProcessing.swift
//  ComposerFeature
//
//  Prepares picked files for 4PDA: HEIC/PNG photos from the library are converted to JPEG
//  (the forum does not accept HEIC) and very large photos are downscaled; thumbnails are made
//  off the main thread.
//

import UIKit
import UniformTypeIdentifiers

enum AttachmentProcessing {
    
    /// Longest side for photos taken from the library. 4PDA re-compresses anyway; this keeps
    /// uploads fast on mobile networks.
    static let maxPhotoDimension: CGFloat = 2560
    
    static func photo(data: Data, suggestedName: String?, contentType: UTType?) -> PickedFile? {
        let isGIF = contentType?.conforms(to: .gif) == true
        if isGIF {
            // Keep animations untouched.
            let name = baseName(suggestedName, fallback: "animation") + ".gif"
            return PickedFile(fileName: name, data: data, isImage: true, thumbnail: thumbnail(from: data))
        }
        guard let image = UIImage(data: data) else { return nil }
        let scaled = downscaled(image, maxDimension: maxPhotoDimension)
        guard let jpeg = scaled.jpegData(compressionQuality: 0.85) else { return nil }
        let name = baseName(suggestedName, fallback: "photo-\(Int(Date().timeIntervalSince1970))") + ".jpg"
        return PickedFile(fileName: name, data: jpeg, isImage: true, thumbnail: thumbnail(from: scaled))
    }
    
    static func pasted(_ image: UIImage, index: Int) -> PickedFile? {
        let scaled = downscaled(image, maxDimension: maxPhotoDimension)
        guard let png = scaled.pngData(), png.count < 2_000_000 else {
            guard let jpeg = scaled.jpegData(compressionQuality: 0.85) else { return nil }
            return PickedFile(fileName: "pasted-\(index).jpg", data: jpeg, isImage: true, thumbnail: thumbnail(from: scaled))
        }
        return PickedFile(fileName: "pasted-\(index).png", data: png, isImage: true, thumbnail: thumbnail(from: scaled))
    }
    
    static func file(url: URL) -> PickedFile? {
        let didAccess = url.startAccessingSecurityScopedResource()
        defer { if didAccess { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url, options: [.mappedIfSafe]) else { return nil }
        let type = UTType(filenameExtension: url.pathExtension)
        let isImage = type?.conforms(to: .image) == true
        return PickedFile(
            fileName: url.lastPathComponent,
            data: data,
            isImage: isImage,
            thumbnail: isImage ? thumbnail(from: data) : nil
        )
    }
    
    // MARK: Helpers
    
    static func downscaled(_ image: UIImage, maxDimension: CGFloat) -> UIImage {
        let size = image.size
        let longest = max(size.width, size.height)
        guard longest > maxDimension, longest > 0 else { return image }
        let scale = maxDimension / longest
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
    }
    
    static func thumbnail(from data: Data) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        return thumbnail(from: image)
    }
    
    static func thumbnail(from image: UIImage) -> Data? {
        image.preparingThumbnail(of: CGSize(width: 160, height: 160))?.jpegData(compressionQuality: 0.7)
            ?? downscaled(image, maxDimension: 160).jpegData(compressionQuality: 0.7)
    }
    
    private static func baseName(_ suggested: String?, fallback: String) -> String {
        guard let suggested, !suggested.isEmpty else { return fallback }
        return (suggested as NSString).deletingPathExtension
    }
}
