//
//  PageMath.swift
//  ForumCore
//
//  Offsets ↔ pages. The forum API is offset based; the UI is page based (1-indexed).
//

import Foundation

public struct PageMath: Equatable, Sendable {
    public var perPage: Int
    public var totalItems: Int
    
    public init(perPage: Int, totalItems: Int) {
        self.perPage = max(1, perPage)
        self.totalItems = max(0, totalItems)
    }
    
    public var pageCount: Int { max(1, (totalItems + perPage - 1) / perPage) }
    
    /// 1-based page for an offset.
    public func page(forOffset offset: Int) -> Int {
        min(pageCount, max(1, offset / perPage + 1))
    }
    
    public func offset(forPage page: Int) -> Int {
        (min(max(1, page), pageCount) - 1) * perPage
    }
    
    /// 1-based page containing the n-th (1-based) item.
    public func page(forItemNumber number: Int) -> Int {
        page(forOffset: max(0, number - 1))
    }
    
    public var lastPageOffset: Int { offset(forPage: pageCount) }
    
    /// Compact list of pages for a paginator: always first/last, a window around current,
    /// `nil` represents an ellipsis.
    public func paginatorItems(current: Int, window: Int = 1) -> [Int?] {
        let count = pageCount
        guard count > 1 else { return [1] }
        let cur = min(max(1, current), count)
        var pages = Set([1, count])
        for p in (cur - window)...(cur + window) where p >= 1 && p <= count { pages.insert(p) }
        let sorted = pages.sorted()
        var result: [Int?] = []
        var prev = 0
        for p in sorted {
            if prev != 0 && p - prev > 1 { result.append(nil) }
            result.append(p)
            prev = p
        }
        return result
    }
}
