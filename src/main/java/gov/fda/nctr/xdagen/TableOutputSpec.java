package gov.fda.nctr.xdagen;

import java.util.*;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import gov.fda.nctr.util.CollFuns;
import gov.fda.nctr.util.Pair;
import static gov.fda.nctr.util.CoreFuns.hashcode;
import static gov.fda.nctr.util.CoreFuns.requireArg;
import static gov.fda.nctr.util.StringFuns.dotQualify;
import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.Field;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.RelId;


public final class TableOutputSpec
{
    private final RelId relId;

    private final DBMD dbmd;

    private final Factory factory;

    private final ChildCollectionsStyle childCollectionsStyle;

    private final String outputXmlNamespace;

    private final List<OutputField> outputFields;

    private final List<Pair<ForeignKey,TableOutputSpec>> childSpecsByFK;

    private final List<Pair<ForeignKey,TableOutputSpec>> parentSpecsByFK;

    private final String rowCollectionElementName;

    private final String rowElementName;

    private final Optional<RowOrdering> rowOrdering;

    private final int hashCode;

    /** Create an output spec with all the includedFields for the passed table/view included but no parents or children.
     If the table name is not qualified by schema, then the DBMD should have an owning schema specified, else
     database metadata may not be found for databases supporting schemas. */
    public TableOutputSpec
    (
        String pqRelName,
        DBMD dbmd,
        Factory factory,
        ChildCollectionsStyle childCollectionsStyle,
        String outputXmlNamespace
    )
    {
        this(dbmd.toRelId(pqRelName), dbmd, factory, childCollectionsStyle, outputXmlNamespace);
    }

    public TableOutputSpec
    (
        RelId relId,
        DBMD dbmd,
        Factory factory,
        ChildCollectionsStyle childCollectionsStyle,
        String outputXmlNamespace
    )
    {
        this(
            relId,
            dbmd,
            factory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    public TableOutputSpec
    (
        RelId relId,
        DBMD dbmd,
        Factory factory,
        ChildCollectionsStyle childCollectionsStyle,
        String outputXmlNamespace,
        Optional<String> rowElName,
        Optional<String> rowCollectionElName
    )
    {
        this(
            relId,
            dbmd,
            factory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.empty(),
            Optional.empty(),
            rowElName,
            rowCollectionElName,
            Optional.empty(),
            Optional.empty()
        );
    }

    /// primary constructor
    private TableOutputSpec
    (
        RelId relId,
        DBMD dbmd,
        Factory factory,
        ChildCollectionsStyle childCollectionsStyle,
        String outputXmlNamespace,
        Optional<List<OutputField>> outputFields,
        Optional<RowOrdering> rowOrdering,
        Optional<String> rowElName,
        Optional<String> rowCollectionElName,
        Optional<List<Pair<ForeignKey,TableOutputSpec>>> includedChildTableSpecs,
        Optional<List<Pair<ForeignKey,TableOutputSpec>>> includedParentTableSpecs
    )
    {
        Objects.requireNonNull(relId);
        Objects.requireNonNull(dbmd);
        Objects.requireNonNull(factory);
        Objects.requireNonNull(childCollectionsStyle);
        Objects.requireNonNull(outputXmlNamespace);
        Objects.requireNonNull(outputFields);
        Objects.requireNonNull(rowOrdering);
        Objects.requireNonNull(rowElName);
        Objects.requireNonNull(rowCollectionElName);
        Objects.requireNonNull(includedChildTableSpecs);
        Objects.requireNonNull(includedParentTableSpecs);

        this.relId = relId;
        this.dbmd = dbmd;
        this.factory = factory;
        this.childCollectionsStyle = childCollectionsStyle;
        this.outputXmlNamespace = outputXmlNamespace;
        this.outputFields = outputFields.isPresent() ? new ArrayList<>(outputFields.get())
                            : getDefaultOutputFields(this.relId);
        this.rowOrdering = rowOrdering;
        this.rowElementName = rowElName.orElseGet(() -> relId.getName().toLowerCase());
        this.rowCollectionElementName = rowCollectionElName.orElseGet(() -> relId.getName().toLowerCase()+"-listing");
        this.childSpecsByFK = includedChildTableSpecs.isPresent() ? new ArrayList<>(includedChildTableSpecs.get())
                              : emptyList();
        this.parentSpecsByFK = includedParentTableSpecs.isPresent() ? new ArrayList<>(includedParentTableSpecs.get())
                               : emptyList();
        this.hashCode = computeHashCode();
    }

    private List<OutputField> getDefaultOutputFields(RelId relid)
    {
        List<OutputField> res = new ArrayList<>();

        for ( Field f: dbmd.getRelationMetaData(relid).getFields() )
            res.add(new OutputField(f, f.getName().toLowerCase()));

        return res;
    }

    public RelId getRelationId()
    {
        return relId;
    }

    public DBMD getDatabaseMetaData()
    {
        return dbmd;
    }

    public Factory getFactory()
    {
        return factory;
    }

    public ChildCollectionsStyle getChildCollectionsStyle()
    {
        return childCollectionsStyle;
    }

    public String getOutputXmlNamespace()
    {
        return outputXmlNamespace;
    }

    public boolean isInlineChildCollections()
    {
        return childCollectionsStyle == ChildCollectionsStyle.INLINE;
    }

    public List<OutputField> getOutputFields()
    {
        return outputFields;
    }

    public String getRowCollectionElementName()
    {
        return rowCollectionElementName;
    }

    public String getRowElementName()
    {
        return rowElementName;
    }

    public Optional<RowOrdering> getRowOrdering()
    {
        return rowOrdering;
    }


    /////////////////////////////////////////////////////////////////////////////////////
    // Retrieval of included child table specifications

    public List<Pair<ForeignKey,TableOutputSpec>> getChildOutputSpecsByFK()
    {
        return new ArrayList<>(childSpecsByFK);
    }

    public List<TableOutputSpec> getChildOutputSpecs()
    {
        List<TableOutputSpec> ospecs = new ArrayList<>();

        for ( Pair<ForeignKey,TableOutputSpec> fkSpecPair: childSpecsByFK )
            ospecs.add(fkSpecPair.snd());

        return ospecs;
    }


    public List<Pair<ForeignKey,TableOutputSpec>> getOutputSpecsByFKForChild(String pqChildRelName)
    {
        return getOutputSpecsByFKForChild(dbmd.toRelId(pqChildRelName));
    }


    /** Obtains all of the current specifications for the indicated child table (which may be included multiple times
        via differing foreign keys), or the empty list if the child table is not currently included. */
    public List<Pair<ForeignKey,TableOutputSpec>> getOutputSpecsByFKForChild(RelId childRelId)
    {
        List<Pair<ForeignKey,TableOutputSpec>> childSpecs = new ArrayList<>();

        for ( Pair<ForeignKey,TableOutputSpec> fkSpecPair: childSpecsByFK )
        {
            if ( fkSpecPair.snd().getRelationId().equals(childRelId) )
                childSpecs.add(fkSpecPair);
        }

        return childSpecs;
    }

    public TableOutputSpec getOutputSpecForChild(String pqChildRelName)
    {
        return getOutputSpecForChild(dbmd.toRelId(pqChildRelName));
    }

    public TableOutputSpec getOutputSpecForChild(RelId childRelId)
    {
        List<Pair<ForeignKey,TableOutputSpec>> childSpecs = getOutputSpecsByFKForChild(childRelId);

        if ( childSpecs.size() == 0 )
            throw new IllegalArgumentException("Table " + childRelId + " has no foreign key links to " + relId + ".");
        else if ( childSpecs.size() > 1 )
            throw new IllegalArgumentException("Child table " + childRelId + " has multiple links to " + relId + ".");
        else
            return childSpecs.get(0).snd();
    }

    // Retrieval of included child table specifications
    /////////////////////////////////////////////////////////////////////////////////////


    /////////////////////////////////////////////////////////////////////////////////////
    // Retrieval of included parent table specifications

    public List<Pair<ForeignKey,TableOutputSpec>> getParentOutputSpecsByFK()
    {
        return new ArrayList<>(parentSpecsByFK);
    }

    public List<TableOutputSpec> getParentOutputSpecs()
    {
        List<TableOutputSpec> specs = new ArrayList<>();

        for ( Pair<ForeignKey,TableOutputSpec> fkSpecPair: parentSpecsByFK )
            specs.add(fkSpecPair.snd());

        return specs;
    }

    public List<Pair<ForeignKey,TableOutputSpec>> getOutputSpecsByFKForParent(String pqParentRelName)
    {
        return getOutputSpecsByFKForParent(dbmd.toRelId(pqParentRelName));
    }


    /** Obtains all of the current specifications for the indicated parent tables (which may be included multiple times under differing foreign keys), or the empty list if the parent
        table is not currently included. */
    public List<Pair<ForeignKey,TableOutputSpec>> getOutputSpecsByFKForParent(RelId parentRelId)
    {
        List<Pair<ForeignKey,TableOutputSpec>> parentSpecs = new ArrayList<>();

        for ( Pair<ForeignKey,TableOutputSpec> fkSpecPair: parentSpecsByFK )
        {
            if ( fkSpecPair.snd().getRelationId().equals(parentRelId) )
                parentSpecs.add(fkSpecPair);
        }

        return parentSpecs;
    }

    public TableOutputSpec getOutputSpecForParent(String pqParentRelName)
    {
        return getOutputSpecForParent(dbmd.toRelId(pqParentRelName));
    }


    public TableOutputSpec getOutputSpecForParent(RelId parentRelId)
    {
        List<Pair<ForeignKey,TableOutputSpec>> parentSpecs = getOutputSpecsByFKForParent(parentRelId);

        if ( parentSpecs.size() == 0 )
            throw new IllegalArgumentException("Table " + relId + " has no foreign key links to " + parentRelId + ".");
        else if ( parentSpecs.size() > 1 )
            throw new IllegalArgumentException("Parent table " + parentRelId + " has multiple foreign key links from " + relId + ".");
        else
            return parentSpecs.get(0).snd();
    }

    // Retrieval of included parent table specifications
    /////////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////////
    // Methods for including a child table in the output

    public TableOutputSpec withChild
    (
        ForeignKey fkFromChild,
        Optional<TableOutputSpec> maybeChildOutputSpec
    )
    {
        requireArg(fkFromChild, "foreign key from child table");

        TableOutputSpec childOutputSpec = maybeChildOutputSpec.orElseGet(() ->
            factory.makeChildTableOutputSpec(fkFromChild, this)
        );

        return new TableOutputSpec(
            relId,
            dbmd,
            factory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(outputFields),
            rowOrdering,
            Optional.of(rowElementName),
            Optional.of(rowCollectionElementName),
            Optional.of(CollFuns.associativeListWithEntry(childSpecsByFK, fkFromChild, childOutputSpec)),
            Optional.of(parentSpecsByFK)
        );
    }

    // Primary withChild implementation, all other withChild methods delegate to this one.
    public TableOutputSpec withChild
    (
        RelId childRelId,
        Optional<Set<String>> reqdFkFieldNames,  // Required if multiple fk's from this child table reference this parent.
        Optional<TableOutputSpec> childOutputSpec
    )
    {
        requireArg(childRelId, "child table relation identifier");

        ForeignKey soughtFk =
            dbmd.getForeignKeyFromTo(
                childRelId,
                relId,
                reqdFkFieldNames.orElse(null),
                DBMD.ForeignKeyScope.REGISTERED_TABLES_ONLY
            );

        if ( soughtFk == null )
            throw new IllegalArgumentException("No foreign key found from table " + childRelId + " to " + relId);

        return withChild(soughtFk, childOutputSpec);
    }

    /** Add a child table for inclusion in this table's output, with default output options.
        The child table should have exactly one foreign key to this table, otherwise the function
        allowing additionally a foreign key to be specified should be called instead. */
    public TableOutputSpec withChild(String pqChildRelName)
    {
        requireArg(pqChildRelName, "child table name");

        return withChild(dbmd.toRelId(pqChildRelName), Optional.empty(), Optional.empty());
    }

    public TableOutputSpec withChild
    (
        String pqChildRelName,
        Optional<Set<String>> reqdFkFieldNames // Required if multiple fk's from this child table reference this parent.
    )
    {
        return withChild(dbmd.toRelId(pqChildRelName), reqdFkFieldNames, Optional.empty());
    }

    /** Add a child table for inclusion in this table's output with specified output options.
        The child table should have exactly one foreign key to this table, otherwise the function
        allowing additionally a foreign key to be specified should be called instead. */
    public TableOutputSpec withChild(TableOutputSpec childOutputSpec) // Required
    {
        requireArg(childOutputSpec, "child table output specification");

        return withChild(childOutputSpec.getRelationId(), Optional.empty(), Optional.of(childOutputSpec));
    }

    public TableOutputSpec withChild
    (
        TableOutputSpec childOutputSpec, // Required
        Optional<Set<String>> reqdFkFieldNames
    )
    {
        requireArg(childOutputSpec, "child table output specification");

        return withChild(childOutputSpec.getRelationId(), reqdFkFieldNames, Optional.of(childOutputSpec));
    }

    /** Convenience method to include all child tables with default options and naming.
     *  The included table output specifications will have default options, so they will
     *  not themselves specify any child or parent tables in their own output.  An
     *  individual table specification added in this manner can be customized by retrieving
     *  it via one of the getSpec* methods and modifying it. The newly added child table
     *  specifications will replace any existing specifications for child tables.*/
    public TableOutputSpec withAllChildTables()
    {
        List<Pair<ForeignKey,TableOutputSpec>> childSpecs = new ArrayList<>();

        for ( ForeignKey fk: dbmd.getForeignKeysFromTo(null, this.relId, DBMD.ForeignKeyScope.REGISTERED_TABLES_ONLY) )
        {
            childSpecs.add(Pair.make(fk, factory.makeChildTableOutputSpec(fk, this)));
        }

        return new TableOutputSpec(
            relId,
            dbmd,
            factory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(outputFields),
            rowOrdering,
            Optional.of(rowElementName),
            Optional.of(rowCollectionElementName),
            Optional.of(childSpecs),
            Optional.of(parentSpecsByFK)
        );
    }

    // Methods for including a child table in the output
    ////////////////////////////////////////////////////////////////////////////////////


    ////////////////////////////////////////////////////////////////////////////////////
    // Methods for including a parent table in the output


    public TableOutputSpec withParent
    (
        ForeignKey fkToParent,
        Optional<TableOutputSpec> maybeParentOutputSpec
    )
    {
        requireArg(fkToParent, "foreign key to parent table");

        TableOutputSpec parentOutputSpec = maybeParentOutputSpec.orElseGet(() ->
            factory.makeParentTableOutputSpec(fkToParent, this)
        );

        return new TableOutputSpec(
            relId,
            dbmd,
            factory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(outputFields),
            rowOrdering,
            Optional.of(rowElementName),
            Optional.of(rowCollectionElementName),
            Optional.of(childSpecsByFK),
            Optional.of(CollFuns.associativeListWithEntry(parentSpecsByFK, fkToParent, parentOutputSpec))
        );
    }

    // Primary withParent implementation, all other withParent methods delegate to this one.
    public TableOutputSpec withParent
    (
        RelId parentRelId,
        Optional<Set<String>> reqdFkFieldNames, // Required if multiple fk's from this table reference the parent.
        Optional<TableOutputSpec> parentOutputSpec
    )
    {
        requireArg(parentRelId, "parent table relation identifier");

        ForeignKey soughtFk =
            dbmd.getForeignKeyFromTo(
                relId,
                parentRelId,
                reqdFkFieldNames.orElse(null),
                DBMD.ForeignKeyScope.REGISTERED_TABLES_ONLY
            );

        if ( soughtFk == null )
            throw new IllegalArgumentException("No foreign key found from table " + relId + " to " + parentRelId);

        return withParent(soughtFk, parentOutputSpec);
    }


    /** Add a parent table for inclusion in this table's output, with default output options.
    The parent table should have exactly one foreign key from this table, otherwise the function
    allowing additionally a foreign key to be specified should be called instead. */
    public TableOutputSpec withParent (String pqParentRelName)
    {
        requireArg(pqParentRelName, "parent table name");

        return withParent(dbmd.toRelId(pqParentRelName), Optional.empty(), Optional.empty()); // default output spec
    }


    public TableOutputSpec withParent
    (
        String pqParentRelName,        // Required, possibly qualified table or view name
        Optional<Set<String>> reqdFkFieldNames   // Required if multiple fk's from this table reference this parent.
    )
    {
        requireArg(pqParentRelName, "parent table name");

        return withParent(dbmd.toRelId(pqParentRelName), reqdFkFieldNames, Optional.empty());

    }


    /** Add a parent table for inclusion in this table's output with specified output options.
    The parent table should have exactly one foreign key from this table, otherwise the function
    allowing additionally a foreign key to be specified should be called instead. */
    public TableOutputSpec withParent(TableOutputSpec parentOutputSpec)
    {
        requireArg(parentOutputSpec, "parent table output specification");

        return withParent(parentOutputSpec.getRelationId(), Optional.empty(), Optional.of(parentOutputSpec));
    }

    public TableOutputSpec withParent
    (
        TableOutputSpec parentOutputSpec,
        Set<String> reqdFkFieldNames   // Required iff multiple fk's from this table reference this parent.
    )
    {
        requireArg(parentOutputSpec, "parent table output specification");

        return withParent(parentOutputSpec.getRelationId(), Optional.of(reqdFkFieldNames), Optional.of(parentOutputSpec));
    }

    /** Convenience method to include all parent tables with default options and naming.
     *  The included table output specifications will have default options, so they will
     *  not themselves specify any child or parent tables in their own output.  An
     *  individual table specification added in this manner can be customized by retrieving
     *  it via one of the getSpec* methods and modifying it.  The newly added parent
     *  specifications will replace any existing specifications for parent tables. */
    public TableOutputSpec withAllParentTables()
    {
        List<Pair<ForeignKey,TableOutputSpec>> parentSpecs = new ArrayList<>();

        for ( ForeignKey fk: dbmd.getForeignKeysFromTo(this.relId, null, DBMD.ForeignKeyScope.REGISTERED_TABLES_ONLY) )
        {
            parentSpecs.add(Pair.make(fk, factory.makeParentTableOutputSpec(fk, this)));
        }

        return new TableOutputSpec(
            relId,
            dbmd,
            factory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(outputFields),
            rowOrdering,
            Optional.of(rowElementName),
            Optional.of(rowCollectionElementName),
            Optional.of(childSpecsByFK),
            Optional.of(parentSpecs)
        );
    }

    // Methods for including a parent table in the output
    ////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////
    // Output fields customization

    public TableOutputSpec withFields(List<OutputField> outputFields)
    {
        return new TableOutputSpec(
            relId,
            dbmd,
            factory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(outputFields),
            rowOrdering,
            Optional.of(rowElementName),
            Optional.of(rowCollectionElementName),
            Optional.of(childSpecsByFK),
            Optional.of(parentSpecsByFK)
        );
    }

    public TableOutputSpec withoutFields(String... dbFieldNames)
    {
        return withoutFields(new HashSet<>(Arrays.asList(dbFieldNames)));
    }

    public TableOutputSpec withoutFields(Set<String> dbFieldNames)
    {
        List<OutputField> remaining = new ArrayList<>();

        for ( OutputField of: outputFields )
        {
            if ( !dbFieldNames.contains(of.getField().getName()) )
                remaining.add(of);
        }


        return new TableOutputSpec(
            relId,
            dbmd,
            factory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(remaining),
            rowOrdering,
            Optional.of(rowElementName),
            Optional.of(rowCollectionElementName),
            Optional.of(childSpecsByFK),
            Optional.of(parentSpecsByFK)
        );
    }

    public TableOutputSpec withoutFieldsOtherThan(String... dbFieldNames)
    {
        return withoutFieldsOtherThan(new HashSet<>(Arrays.asList(dbFieldNames)));
    }


    public TableOutputSpec withoutFieldsOtherThan(Set<String> dbFieldNames)
    {
        List<OutputField> remaining = new ArrayList<>();

        for ( OutputField ofield: outputFields )
        {
            if ( dbFieldNames.contains(ofield.getField().getName()) )
                remaining.add(ofield);
        }

        return new TableOutputSpec(
            relId,
            dbmd,
            factory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(remaining),
            rowOrdering,
            Optional.of(rowElementName),
            Optional.of(rowCollectionElementName),
            Optional.of(childSpecsByFK),
            Optional.of(parentSpecsByFK)
        );
    }

    public TableOutputSpec withFieldAsElement(String dbFieldName, String outputElName)
    {
        List<OutputField> outputFields = new ArrayList<>();

        for ( OutputField ofield: this.outputFields)
        {
            if ( ofield.getField().getName().equals(dbFieldName) )
                outputFields.add(new OutputField(ofield.getField(), outputElName));
            else
                outputFields.add(ofield);
        }

        return new TableOutputSpec(
            relId,
            dbmd,
            factory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(outputFields),
            rowOrdering,
            Optional.of(rowElementName),
            Optional.of(rowCollectionElementName),
            Optional.of(childSpecsByFK),
            Optional.of(parentSpecsByFK)
        );
    }

    // Output fields customization
    ///////////////////////////////////////////////////////////////////////////////////


    ////////////////////////////////////////////////////////////////////////////////////
    // Row ordering customization


    public TableOutputSpec orderedBy(RowOrdering newRowOrdering)
    {
        return new TableOutputSpec(
            relId,
            dbmd,
            factory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(outputFields),
            Optional.of(newRowOrdering),
            Optional.of(rowElementName),
            Optional.of(rowCollectionElementName),
            Optional.of(childSpecsByFK),
            Optional.of(parentSpecsByFK)
       );
    }

    // Row ordering customization
    ////////////////////////////////////////////////////////////////////////////////////



    ///////////////////////////////////////////////////////////////////////////////////
    // Factory customization

    public TableOutputSpec withFactory(Factory newFactory)
    {
        return new TableOutputSpec(
            relId,
            dbmd,
            newFactory,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(outputFields),
            rowOrdering,
            Optional.of(rowElementName),
            Optional.of(rowCollectionElementName),
            Optional.of(childSpecsByFK),
            Optional.of(parentSpecsByFK)
        );
    }

    // Factory customization
    ///////////////////////////////////////////////////////////////////////////////////


    @Override
    public int hashCode() { return hashCode; }

    private int computeHashCode()
    {
        return
            hashcode(relId)
            + hashcode(dbmd)
            + hashcode(factory)
            + childCollectionsStyle.hashCode()
            + hashcode(outputXmlNamespace)
            + hashcode(outputFields)
            + hashcode(childSpecsByFK)
            + hashcode(parentSpecsByFK)
            + hashcode(rowElementName)
            + hashcode(rowCollectionElementName)
            + hashcode(rowOrdering.orElse(null));
    }

    @Override
    public boolean equals(Object o)
    {
        if ( !(o instanceof TableOutputSpec) )
            return false;
        else
        {
            final TableOutputSpec tos = (TableOutputSpec)o;

            if (this == tos )
                return true;
            else
            {
                if ( hashCode != tos.hashCode )
                    return false;
                else
                    return Objects.equals(relId,tos.relId)
                        && Objects.equals(dbmd, tos.dbmd)
                        && Objects.equals(factory, tos.factory)
                        && Objects.equals(childCollectionsStyle, tos.childCollectionsStyle)
                        && Objects.equals(outputXmlNamespace, tos.outputXmlNamespace)
                        && Objects.equals(outputFields, tos.outputFields)
                        && Objects.equals(childSpecsByFK, tos.childSpecsByFK)
                        && Objects.equals(parentSpecsByFK, tos.parentSpecsByFK)
                        && Objects.equals(rowCollectionElementName, tos.rowCollectionElementName)
                        && Objects.equals(rowElementName, tos.rowElementName)
                        && Objects.equals(rowOrdering.orElse(null), tos.rowOrdering.orElse(null));
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes and interfaces

    public static class OutputField
    {
        private final Field field;
        private final String outputElementName;

        public OutputField(Field field, String outputElementName)
        {
            this.field = requireArg(field, "field");
            this.outputElementName = requireArg(outputElementName, "output element name");
        }

        public Field getField() { return field; }

        public String getOutputElementName() { return outputElementName; }


        @Override
        public int hashCode()
        {
            return field.hashCode() + 31*outputElementName.hashCode();
        }

        @Override
        public boolean equals(Object o)
        {
            if ( !(o instanceof OutputField) )
                return false;
            OutputField other = (OutputField)o;
            return this.field.equals(other.field) && this.outputElementName.equals(other.outputElementName);
        }
    }

    public static abstract class RowOrdering
    {
        // Get a list of expressions to order by, in terms of the table fields and the passed field qualifying alias.
        public abstract List<String> getOrderByExpressions(String fieldQualifyingAlias);

        /** Convenience method for constructing order by expressions for field names. */

        public static RowOrdering fields(final String... fieldNames)
        {
            /** Convenience method for constructing order by expressions for field names.
             *  The field names may optionally including a trailing " asc" or " desc" to specify sort direction.
             */
            return new RowOrdering() {
                public List<String> getOrderByExpressions(String fieldQualifyingAlias)
                {
                    return dotQualify(asList(fieldNames), fieldQualifyingAlias);
                }
            };
        }

        /** Convenience method for constructing order by expressions for field names.
         *  The field names may optionally including a trailing " asc" or " desc" to specify sort direction.
         */
        public static RowOrdering fields(final List<String> fieldNames)
        {
            return new RowOrdering() {
                public List<String> getOrderByExpressions(String fieldQualifyingAlias)
                {
                    return dotQualify(fieldNames, fieldQualifyingAlias);
                }
            };
        }
    }

    public interface Factory
    {
        // These two functions are convenience methods intended for direct use by clients.
        TableOutputSpec table(RelId relId);
        TableOutputSpec table(String pqTableName); // possibly qualified table name

        // These methods are used internally by TableOutputSpec to create parent/child output specs when they aren't specified when adding parent or child tables.
        TableOutputSpec makeChildTableOutputSpec(ForeignKey fkFromChild, TableOutputSpec attachedToOspec);
        TableOutputSpec makeParentTableOutputSpec(ForeignKey fkToParent, TableOutputSpec attachedToOspec);
    }

    // Inner classes and interfaces
    ////////////////////////////////////////////////////////////////////////////////////////////////
}
