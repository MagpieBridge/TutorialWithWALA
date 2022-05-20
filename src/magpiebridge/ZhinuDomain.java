package magpiebridge;

import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.MutableMapping;
import java.util.List;

/** set of tainted value numbers for each node */
public class ZhinuDomain extends MutableMapping<Pair<Integer, List<CAstSourcePositionMap.Position>>>
    implements TabulationDomain<
        Pair<Integer, List<CAstSourcePositionMap.Position>>,
        BasicBlockInContext<IExplodedBasicBlock>> {

  private static final long serialVersionUID = -1897766113586243833L;

  @Override
  public boolean hasPriorityOver(
      PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p1,
      PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p2) {
    // don't worry about worklist priorities
    return false;
  }
}
