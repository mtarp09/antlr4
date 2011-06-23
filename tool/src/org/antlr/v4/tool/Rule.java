package org.antlr.v4.tool;

import org.antlr.runtime.Token;
import org.antlr.v4.parse.ANTLRParser;
import org.stringtemplate.v4.misc.MultiMap;

import java.util.*;

public class Rule implements AttributeResolver {
	    /** Rule refs have a predefined set of attributes as well as
     *  the return values and args.
     */
    public static AttributeDict predefinedRulePropertiesDict =
        new AttributeDict(AttributeDict.DictType.PREDEFINED_RULE) {{
            add(new Attribute("text"));
            add(new Attribute("start"));
            add(new Attribute("stop"));
            add(new Attribute("tree"));
            add(new Attribute("st"));
        }};

    public static AttributeDict predefinedTreeRulePropertiesDict =
        new AttributeDict(AttributeDict.DictType.PREDEFINED_TREE_RULE) {{
            add(new Attribute("text"));
            add(new Attribute("start")); // note: no stop; not meaningful
            add(new Attribute("tree"));
            add(new Attribute("st"));
        }};

    public static AttributeDict predefinedLexerRulePropertiesDict =
        new AttributeDict(AttributeDict.DictType.PREDEFINED_LEXER_RULE) {{
            add(new Attribute("text"));
            add(new Attribute("type"));
            add(new Attribute("line"));
            add(new Attribute("index"));
            add(new Attribute("pos"));
            add(new Attribute("channel"));
            add(new Attribute("start"));
            add(new Attribute("stop"));
            add(new Attribute("int"));
        }};

    public String name;
	public List<GrammarAST> modifiers;

    public RuleAST ast;
    public AttributeDict args;
    public AttributeDict retvals;
    public AttributeDict scope; // scope { int i; }

	/** A list of scope names used by this rule */
    public List<Token> useScopes;

	public Grammar g;

	/** If we're in a lexer grammar, we might be in a mode */
	public String mode;

    /** Map a name to an action for this rule like @init {...}.
     *  The code generator will use this to fill holes in the rule template.
     *  I track the AST node for the action in case I need the line number
     *  for errors.
     */
    public Map<String, ActionAST> namedActions =
        new HashMap<String, ActionAST>();

    /** Track exception handler actions (exception type is prev child);
	 *  don't track finally action
	 */
    public List<ActionAST> exceptionActions = new ArrayList<ActionAST>();

	public ActionAST finallyAction;

	public int numberOfAlts;

	public boolean isStartRule = true; // nobody calls us

	public Alternative[] alt;

	/** All rules have unique index 0..n-1 */
	public int index;

	public int actionIndex; // if lexer

	public Rule(Grammar g, String name, RuleAST ast, int numberOfAlts) {
		this.g = g;
		this.name = name;
		this.ast = ast;
		this.numberOfAlts = numberOfAlts;
		alt = new Alternative[numberOfAlts+1]; // 1..n
		for (int i=1; i<=numberOfAlts; i++) alt[i] = new Alternative(this);
	}

	public void defineActionInAlt(int currentAlt, ActionAST actionAST) {
		alt[currentAlt].actions.add(actionAST);
		if ( g.isLexer() || actionAST.getType()== ANTLRParser.FORCED_ACTION ) {
			actionIndex = g.actions.size() + 1;
			g.actions.put(actionAST, actionIndex);
		}
	}

	public void definePredicateInAlt(int currentAlt, PredAST predAST) {
		alt[currentAlt].actions.add(predAST);
		g.sempreds.put(predAST, g.sempreds.size() + 1);
	}

	public Attribute resolveRetvalOrProperty(String y) {
		if ( retvals!=null ) {
			Attribute a = retvals.get(y);
			if ( a!=null ) return a;
		}
		AttributeDict d = getPredefinedScope(LabelType.RULE_LABEL);
		return d.get(y);
	}

	public Set<String> getTokenRefs() {
        Set<String> refs = new HashSet<String>();
		for (int i=1; i<=numberOfAlts; i++) {
			refs.addAll(alt[i].tokenRefs.keySet());
		}
		return refs;
    }

    public Set<String> getLabelNames() {
        Set<String> refs = new HashSet<String>();
        for (int i=1; i<=numberOfAlts; i++) {
            refs.addAll(alt[i].labelDefs.keySet());
        }
        return refs;
    }

	// TODO: called frequently; make it more efficient
    public MultiMap<String, LabelElementPair> getLabelDefs() {
        MultiMap<String, LabelElementPair> defs =
            new MultiMap<String, LabelElementPair>();
        for (int i=1; i<=numberOfAlts; i++) {
            for (List<LabelElementPair> pairs : alt[i].labelDefs.values()) {
                for (LabelElementPair p : pairs) {
                    defs.map(p.label.getText(), p);
                }
            }
        }
        return defs;
    }

		/**  $x		Attribute: rule arguments, return values, predefined rule prop.
	 */
	public Attribute resolveToAttribute(String x, ActionAST node) {
		if ( args!=null ) {
			Attribute a = args.get(x);   	if ( a!=null ) return a;
		}
		if ( retvals!=null ) {
			Attribute a = retvals.get(x);	if ( a!=null ) return a;
		}
		AttributeDict properties = getPredefinedScope(LabelType.RULE_LABEL);
		return properties.get(x);
	}

	/** $x.y	Attribute: x is surrounding rule, label ref (in any alts) */
	public Attribute resolveToAttribute(String x, String y, ActionAST node) {
		if ( this.name.equals(x) ) { // x is this rule?
			return resolveToAttribute(y, node);
		}
		LabelElementPair anyLabelDef = getAnyLabelDef(x);
		if ( anyLabelDef!=null ) {
			if ( anyLabelDef.type==LabelType.RULE_LABEL ) {
				return g.getRule(anyLabelDef.element.getText()).resolveRetvalOrProperty(y);
			}
			else {
				return getPredefinedScope(anyLabelDef.type).get(y);
			}
		}
		return null;

	}

	public AttributeDict resolveToDynamicScope(String x, ActionAST node) {
		Rule r = resolveToRule(x);
		if ( r!=null && r.scope!=null ) return r.scope;
		return g.scopes.get(x);
	}

	public boolean resolvesToLabel(String x, ActionAST node) {
		return false;
	}

	public boolean resolvesToListLabel(String x, ActionAST node) {
		LabelElementPair anyLabelDef = getAnyLabelDef(x);
		return anyLabelDef!=null &&
			   (anyLabelDef.type==LabelType.RULE_LIST_LABEL ||
				anyLabelDef.type==LabelType.TOKEN_LIST_LABEL);
	}

	public boolean resolvesToToken(String x, ActionAST node) {
		LabelElementPair anyLabelDef = getAnyLabelDef(x);
		if ( anyLabelDef!=null && anyLabelDef.type==LabelType.TOKEN_LABEL ) return true;
		return false;
	}

	public boolean resolvesToAttributeDict(String x, ActionAST node) {
		if ( resolvesToToken(x, node) ) return true;
		if ( x.equals(name) ) return true; // $r for action in rule r, $r is a dict
		if ( scope!=null ) return true;
		if ( g.scopes.get(x)!=null ) return true;
		return false;
	}

	public Rule resolveToRule(String x) {
		if ( x.equals(this.name) ) return this;
		LabelElementPair anyLabelDef = getAnyLabelDef(x);
		if ( anyLabelDef!=null && anyLabelDef.type==LabelType.RULE_LABEL ) {
			return g.getRule(anyLabelDef.element.getText());
		}
		return g.getRule(x);
	}

	public LabelElementPair getAnyLabelDef(String x) {
		List<LabelElementPair> labels = getLabelDefs().get(x);
		if ( labels!=null ) return labels.get(0);
		return null;
	}

    public AttributeDict getPredefinedScope(LabelType ltype) {
        String grammarLabelKey = g.getTypeString() + ":" + ltype;
        return Grammar.grammarAndLabelRefTypeToScope.get(grammarLabelKey);
    }

	public boolean isFragment() {
		if ( modifiers==null ) return false;
		for (GrammarAST a : modifiers) {
			if ( a.getText().equals("fragment") ) return true;
		}
		return false;
	}

    @Override
    public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("Rule{name="+name);
		if ( args!=null ) buf.append(", args=" + args);
		if ( retvals!=null ) buf.append(", retvals=" + retvals);
		if ( scope!=null ) buf.append(", scope=" + scope);
		buf.append("}");
		return buf.toString();
    }
}