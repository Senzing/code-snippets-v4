// This file is used by Code Analysis to maintain SuppressMessage
// attributes that are applied to this project.
// Project-level suppressions either have no target or are given
// a specific target and scoped to a namespace, type, member, etc.

using System.Diagnostics.CodeAnalysis;

[assembly: SuppressMessage("Performance", "CA1854:Prefer the 'IDictionary.TryGetValue(TKey, out TValue)' method", Justification = "Nullable Dictionary making for less readable code.")]
[assembly: SuppressMessage("Usage", "CA2201:Do not raise reserved exception types", Justification = "These are examples and there is no need to use more specific exceptions")]
[assembly: SuppressMessage("Performance", "CA1859:Use concrete types when possible for improved performance", Justification = "It is better to OOP principle to use interfaces rather than concrete types")]
