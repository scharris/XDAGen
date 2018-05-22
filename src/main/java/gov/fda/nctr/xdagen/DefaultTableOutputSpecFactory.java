package gov.fda.nctr.xdagen;

import java.util.*;

import gov.fda.nctr.dbmd.DBMD;
import gov.fda.nctr.dbmd.ForeignKey;
import gov.fda.nctr.dbmd.RelId;
import static gov.fda.nctr.util.StringFuns.lc;
import static gov.fda.nctr.util.StringFuns.stringFrom;
import static gov.fda.nctr.xdagen.ChildCollectionsStyle.INLINE;


public class DefaultTableOutputSpecFactory implements TableOutputSpec.Factory
{
    private final DBMD dbmd;

    private final ElementNamer elementNamer;

    private final String outputXmlNamespace;

    private final ChildCollectionsStyle childCollectionsStyle;

    public DefaultTableOutputSpecFactory
    (
        DBMD dbmd,
        ChildCollectionsStyle childCollectionsStyle,
        String outputXmlNamespace
    )
    {
        this(dbmd, outputXmlNamespace, childCollectionsStyle, new DefaultElementNamer(dbmd, childCollectionsStyle));
    }

    public DefaultTableOutputSpecFactory
    (
        DBMD dbmd,
        String outputXmlNamespace,
        ChildCollectionsStyle childCollectionsStyle,
        ElementNamer elementNamer
    )
    {
        Objects.requireNonNull(dbmd);
        Objects.requireNonNull(outputXmlNamespace);
        Objects.requireNonNull(childCollectionsStyle);
        Objects.requireNonNull(elementNamer);

        this.dbmd = dbmd;
        this.outputXmlNamespace = outputXmlNamespace;
        this.childCollectionsStyle = childCollectionsStyle;
        this.elementNamer = elementNamer;
    }

    @Override
    public TableOutputSpec table(RelId relId)
    {
        return new TableOutputSpec(
            relId,
            dbmd,
            this,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(elementNamer.getDefaultRowElementName(relId)),
            Optional.of(elementNamer.getDefaultRowCollectionElementName(relId))
        );
    }

    @Override
    public TableOutputSpec table(String pqTableName)
    {
        return table(dbmd.toRelId(pqTableName));
    }


    @Override
    public TableOutputSpec makeChildTableOutputSpec
    (
        ForeignKey fkFromChild,
        TableOutputSpec attachedToOspec
    )
    {
        RelId childRelId = fkFromChild.getSourceRelationId();
        RelId withinRelId = attachedToOspec.getRelationId();

        Set<String> fkFieldNames = new HashSet<>(fkFromChild.getSourceFieldNames());

        String rowElName = elementNamer.getChildRowElementNameWithinParent(childRelId, withinRelId, Optional.of(fkFieldNames));
        String rowCollElName = elementNamer.getChildRowCollectionElementNameWithinParent(childRelId, withinRelId, Optional.of(fkFieldNames));

        return new TableOutputSpec(
            childRelId,
            dbmd,
            this,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(rowElName),
            Optional.of(rowCollElName)
        );
    }

    @Override
    public TableOutputSpec makeParentTableOutputSpec
    (
        ForeignKey fkToParent,
        TableOutputSpec attachedToOspec
    )
    {
        RelId parentRelid = fkToParent.getTargetRelationId();
        RelId withinRelid = attachedToOspec.getRelationId();

        Set<String> fkFieldNames = new HashSet<>(fkToParent.getSourceFieldNames());

        String rowElName =
            elementNamer.getParentRowElementNameWithinChild(
                withinRelid,
                parentRelid,
                Optional.of(fkFieldNames)
            );

        return new TableOutputSpec(
            parentRelid,
            dbmd,
            this,
            childCollectionsStyle,
            outputXmlNamespace,
            Optional.of(rowElName),
            Optional.empty()
        );
    }

    public interface ElementNamer
    {
        String getDefaultRowElementName(RelId relId);

        String getDefaultRowCollectionElementName(RelId relId);

        String getChildRowElementNameWithinParent(
            RelId childRelId,
            RelId parentRelId,
            Optional<Set<String>> fkFieldNames // set of child fk field names used to disambiguate fk's
        );

        String getChildRowCollectionElementNameWithinParent(
            RelId childRelId,
            RelId parentRelId,
            Optional<Set<String>> fkFieldName // set of child fk field names used to disambiguate fk's
        );

        String getParentRowElementNameWithinChild(
            RelId childRelId,
            RelId parentRelId,
            Optional<Set<String>> fkFieldNames
        );
    }

    public static class DefaultElementNamer implements ElementNamer
    {
        private final DBMD dbmd;
        private final ChildCollectionsStyle childCollectionsStyle;

        public DefaultElementNamer(DBMD dbmd, ChildCollectionsStyle childCollectionsStyle)
        {
            this.dbmd = dbmd;
            this.childCollectionsStyle = childCollectionsStyle;
        }

        @Override
        public String getDefaultRowElementName(RelId relId)
        {
            return relId.getName().toLowerCase();
        }

        @Override
        public String getDefaultRowCollectionElementName(RelId relId)
        {
            return relId.getName().toLowerCase() + "-listing";
        }


        /** Returns a simple name based on the child relation id alone, unless:
         *  1) there are multiple fks from this child to parent, and
         *  2) the set of fk field names has been specified.
        */
        @Override
        public String getChildRowCollectionElementNameWithinParent
        (
            RelId childRelId,
            RelId parentRelId,
            Optional<Set<String>> fkFieldName
        )
        {
            // Are there multiple fk's from this child to this parent?  If so, use a fully qualified name if we have enough information to (fkFieldNames).
            List<ForeignKey> fks = dbmd.getForeignKeysFromTo(childRelId, parentRelId);

            if ( fks.size() <= 1 || !fkFieldName.isPresent() )
            {
                return getDefaultRowCollectionElementName(childRelId);
            }
            else
            {
                ForeignKey fk = dbmd.getForeignKeyHavingFieldSetAmong(fkFieldName.get(), fks);

                if ( fk == null )
                    throw new IllegalArgumentException("Foreign key with field set " + fkFieldName.get() + " not found from " +
                    childRelId + " to " + parentRelId + ".");

                return getFullyQualifiedChildRowCollectionElementNameWithinParent(fk);
            }
        }

        @Override
        public String getChildRowElementNameWithinParent
        (
            RelId childRelId,
            RelId parentRelId,
            Optional<Set<String>> fkFieldNames
        )
        {
            if ( childCollectionsStyle == ChildCollectionsStyle.WRAPPED ) // Wrapped child row elements don't need qualified names.
                return getDefaultRowElementName(childRelId);
            else  // INLINE (child-) collection elements case
            {
                // Are there multiple fk's from this child to this parent?
                // If so this one will need to be distinguished with a qualified name.
                List<ForeignKey> child_fks_to_parent = dbmd.getForeignKeysFromTo(childRelId, parentRelId);

                // Is this child table also a parent of the parent table?
                // If so, this child table's child elements in the parent will need to be distinguished from the possible parent entries.
                List<ForeignKey> parent_fks_to_child = dbmd.getForeignKeysFromTo(parentRelId, childRelId); // yes, parent->child on purpose here!

                boolean needQualifiedName = child_fks_to_parent.size() > 1 || parent_fks_to_child.size() > 0;

                boolean canFormQualifiedName = fkFieldNames.isPresent();

                if ( needQualifiedName && canFormQualifiedName )
                {
                    // We find the actual foreign key so we can get a predictable properly ordered list of the foreign key names when forming the names.
                    ForeignKey fk = dbmd.getForeignKeyHavingFieldSetAmong(fkFieldNames.get(), child_fks_to_parent);

                    if ( fk == null )
                        throw new IllegalArgumentException("Foreign key with field set " + fkFieldNames + " not found from " +
                        childRelId + " to " + parentRelId + ".");

                    if ( child_fks_to_parent.size() == 1 ) // This is the only fk from this child to the parent, so we only need to distinguish this link from the parent links.
                        return "child-" + getDefaultRowElementName(fk.getSourceRelationId());
                    else
                        return getFullyQualifiedChildRowElementNameWithinParent(fk);
                }
                else // Just make a simple unqualified name
                {
                    return getDefaultRowElementName(childRelId);
                }
            }

        }

        @Override
        public String getParentRowElementNameWithinChild
        (
            RelId childRelId,
            RelId parentRelId,
            Optional<Set<String>> fkFieldNames
        )
        {
            // If this is a multiple parent for this child, we'll need a qualfied name.
            List<ForeignKey> childFksToParent = dbmd.getForeignKeysFromTo(childRelId, parentRelId);

            // For the case of inline (child) collection elements, make sure the parent rel is not also a child of the child rel, which could also cause a name conflict.
            List<ForeignKey> parentFksToChild =
                childCollectionsStyle == INLINE ? dbmd.getForeignKeysFromTo(parentRelId, childRelId) // yes, parent->child on purpose here!
                : null;

            boolean needQualifiedName =
                childFksToParent.size() > 1 ||
                (childCollectionsStyle == INLINE && parentFksToChild.size() > 0);

            boolean canFormQualifiedName = fkFieldNames.isPresent();

            if ( needQualifiedName && canFormQualifiedName )
            {
                ForeignKey fk = dbmd.getForeignKeyHavingFieldSetAmong(fkFieldNames.get(), childFksToParent);

                if ( fk == null )
                    throw new IllegalArgumentException("Foreign key with field set " + fkFieldNames + " not found from " +
                    childRelId + " to " + parentRelId + ".");

                if ( childFksToParent.size() == 1 ) // There aren't multiple fks to this parent from the child, so just distinguish it from the children.
                    return "parent-" + getDefaultRowElementName(fk.getTargetRelationId());
                else
                    return getFullyQualifiedParentRowElementNameWithinChild(fk);
            }
            else // Just make a simple unqualified name.
            {
                return getDefaultRowElementName(parentRelId);
            }
        }

        private String getFullyQualifiedChildRowCollectionElementNameWithinParent(ForeignKey fk)
        {
            return getDefaultRowCollectionElementName(fk.getSourceRelationId()) + "-from-" + stringFrom(lc(fk.getSourceFieldNames()),"-");
        }

        public String getFullyQualifiedChildRowElementNameWithinParent(ForeignKey fk)
        {
            return getDefaultRowElementName(fk.getSourceRelationId()) + "-child-referencing-via-" + stringFrom(lc(fk.getSourceFieldNames()),"-");
        }

        private String getFullyQualifiedParentRowElementNameWithinChild(ForeignKey fk)
        {
            return getDefaultRowElementName(fk.getTargetRelationId()) + "-parent-referenced-via-" + stringFrom(lc(fk.getSourceFieldNames()),"-");
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + dbmd.hashCode();
            result = prime * result + childCollectionsStyle.hashCode();
            return result;
        }


        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            DefaultElementNamer other = (DefaultElementNamer) obj;
            if ( childCollectionsStyle != other.childCollectionsStyle )
                return false;
            return dbmd.equals(other.dbmd);
        }
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + dbmd.hashCode();
        result = prime * result + outputXmlNamespace.hashCode();
        result = prime * result + childCollectionsStyle.hashCode();
        result = prime * result + elementNamer.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null  || getClass() != obj.getClass())
            return false;
        DefaultTableOutputSpecFactory other = (DefaultTableOutputSpecFactory) obj;
        if (!dbmd.equals(other.dbmd))
            return false;
        if ( !outputXmlNamespace.equals(other.outputXmlNamespace) )
            return false;
        if ( childCollectionsStyle != other.childCollectionsStyle )
            return false;
        if (!elementNamer.equals(other.elementNamer))
            return false;
        return true;
    }
}
