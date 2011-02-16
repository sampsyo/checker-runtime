package checkers.runtime.jillexample;

import checkers.quals.TypeQualifiers;
import checkers.quals.Unqualified;
import checkers.runtime.InstrumentingChecker;

@TypeQualifiers({Unqualified.class})
public class ExampleChecker extends InstrumentingChecker {
    @Override
    public ExampleInstrumentor getInstrumentor() {
        return new ExampleInstrumentor();
    }
}
