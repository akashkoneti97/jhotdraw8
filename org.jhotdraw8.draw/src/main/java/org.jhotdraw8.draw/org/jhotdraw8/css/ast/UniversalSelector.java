/*
 * @(#)UniversalSelector.java
 * Copyright © 2022 The authors and contributors of JHotDraw. MIT License.
 */
package org.jhotdraw8.css.ast;

import org.jhotdraw8.annotation.NonNull;
import org.jhotdraw8.annotation.Nullable;
import org.jhotdraw8.css.CssToken;
import org.jhotdraw8.css.CssTokenType;
import org.jhotdraw8.css.SelectorModel;

import java.util.function.Consumer;

/**
 * A "universal selector" matches an element if the element exists.
 *
 * @author Werner Randelshofer
 */
public class UniversalSelector extends SimpleSelector {

    public UniversalSelector() {
    }

    @Override
    public @NonNull String toString() {
        return "Universal:*";
    }

    @Override
    public @Nullable <T> T match(SelectorModel<T> model, T element) {
        return element;
    }

    @Override
    public int getSpecificity() {
        return 0;
    }

    @Override
    public void produceTokens(@NonNull Consumer<CssToken> consumer) {
        consumer.accept(new CssToken(CssTokenType.TT_ASTERISK));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return UniversalSelector.class.hashCode();
    }
}
