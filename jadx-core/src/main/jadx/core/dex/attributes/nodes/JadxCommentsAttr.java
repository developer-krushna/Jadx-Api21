package jadx.core.dex.attributes.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.*;
import jadx.api.CommentsLevel;
import jadx.api.plugins.input.data.attributes.IJadxAttrType;
import jadx.api.plugins.input.data.attributes.IJadxAttribute;
import jadx.core.dex.attributes.AType;
import jadx.core.utils.Utils;

public class JadxCommentsAttr implements IJadxAttribute {

	private final Map<CommentsLevel, List<String>> comments = new EnumMap<>(CommentsLevel.class);

	public void add(CommentsLevel level, String comment) {
    List<String> commentsList = comments.get(level);
    if (commentsList == null) {
        commentsList = new ArrayList<>();
        comments.put(level, commentsList);
    }
    commentsList.add(comment);
}

public List<String> formatAndFilter(CommentsLevel level) {
    if (level == CommentsLevel.NONE || level == CommentsLevel.USER_ONLY) {
        return Collections.emptyList();
    }
    List<String> filteredComments = new ArrayList<>();
    for (Map.Entry<CommentsLevel, List<String>> entry : comments.entrySet()) {
        CommentsLevel entryLevel = entry.getKey();
        if (entryLevel.filter(level)) {
            String levelName = entryLevel.name();
            for (String comment : entry.getValue()) {
                filteredComments.add("JADX " + levelName + ": " + comment);
            }
        }
    }
    Collections.sort(filteredComments);
    return filteredComments;
}
	public Map<CommentsLevel, List<String>> getComments() {
		return comments;
	}

	@Override
	public IJadxAttrType<JadxCommentsAttr> getAttrType() {
		return AType.JADX_COMMENTS;
	}

	@Override
	public String toString() {
		return "JadxCommentsAttr{\n "
				+ Utils.listToString(comments.entrySet(), "\n ",
						e -> e.getKey() + ": \n -> " + Utils.listToString(e.getValue(), "\n -> "))
				+ '}';
	}
}
