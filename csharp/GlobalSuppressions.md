# Global Suppression Notes

## Overview

Senzing has chosen to suppress some of the default coding guidance for .NET development in the
provided examples and code snippets via the use of `GlobalSuppressions.cs` files.  This is done
to favor of readability and clarity of the examples and, in places, to encourage developers to
implement code that is more robust and easily maintained.  The reasoning for each such suppression
is given here along with alternatives if you instead choose to follow the guidance from
the suppressed directives.

### CA1859: Use concrete types when possible for improved performance

Senzing encourages developers to use the `Senzing.Sdk.SzEnvironment` interface in favor of its concrete
implementation type throughout their code with the exception of the construction and initialization of
the `Senzing.Sdk.SzEnvironment` instance.  This is encouraged so that your code can easily interchange one
implementation for another without concerns for dependencies on methods that may be specific to a concrete
implementation type.  For example, one might swap `SzCoreEnvironment` for an open-source `SzGrpcEnvironment`
on a single line for initialization without concerns for searching out incompatibilities that might be
caused elsewhere in the code due to such a change.

In short, Senzing feels that in the case of `SzEnvironment` the performance concerns referenced by `CA1859`
are insignificant and negligible especially when compared to the cost of sacrificing good Object-Oriented
Programming principles that would otherwise improve the maintainability of your source code.

Therefore, Senzing encourages:

```java
// initialize the Senzing environment
SzEnvironment env = SzCoreEnvironment.NewBuilder()
                                     .Settings(settings)
                                     .InstanceName(instanceName)
                                     .VerboseLogging(false)
                                     .Build();
```

Over:

```java
// initialize the Senzing environment
SzCoreEnvironment env = SzCoreEnvironment.NewBuilder()
                                         .Settings(settings)
                                         .InstanceName(instanceName)
                                         .VerboseLogging(false)
                                         .Build();
```

If, however, you prefer to adhere to the guidance encouraged by `CA1859`, then simply use the
concrete type in your declaration of the environment variable **OR** use the `var` keyword in
place of a specific type name.
