package jillexample;

import checkers.quals.TypeQualifiers;
import checkers.quals.TypeQualifier;
import checkers.quals.SubtypeOf;

import jill.InstrumentingChecker;

// In this example, we're not using any type qualifiers -- just doing
// instrumentation. Unfortunately, the Checker Framework requires us to
// provide at least two annotations.
@TypeQualifier
@SubtypeOf({})
@interface DummyQual1 { }
@TypeQualifier
@SubtypeOf({})
@interface DummyQual2 { }

@TypeQualifiers({DummyQual1.class, DummyQual2.class})
public class ExampleChecker extends InstrumentingChecker {
    @Override
    public ExampleInstrumentor getInstrumentor() {
        return new ExampleInstrumentor();
    }
}
